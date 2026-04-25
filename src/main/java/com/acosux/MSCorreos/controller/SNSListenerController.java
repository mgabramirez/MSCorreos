package com.acosux.MSCorreos.controller;

import com.acosux.MSCorreos.application.usecases.ProcesarEventoTrackingUseCase;
import com.acosux.MSCorreos.dtos.TrackingEventDTO;
import com.acosux.MSCorreos.infrastructure.adapters.sns.SNSMessageValidator;
import com.acosux.MSCorreos.infrastructure.exceptions.SNSSignatureValidationException;
import com.acosux.MSCorreos.infrastructure.exceptions.TrackingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URL;

/**
 * Controlador REST que recibe notificaciones de Amazon SNS para eventos de tracking de SES.
 *
 * <p>Implementa el protocolo HTTP de suscripción SNS:</p>
 * <ul>
 *   <li>{@code SubscriptionConfirmation}: confirma la suscripción llamando al SubscribeURL</li>
 *   <li>{@code UnsubscribeConfirmation}: maneja la desuscripción</li>
 *   <li>{@code Notification}: valida firma y procesa el evento de tracking</li>
 * </ul>
 *
 * <p>SNS requiere que el endpoint siempre retorne HTTP 200 para evitar reintentos,
 * excepto en caso de firma inválida donde se retorna HTTP 403.</p>
 *
 * <p>Requirements: 5.1, 5.2, 13.2</p>
 *
 * @author MSCorreos Team
 */
@RestController
@RequestMapping("/api/v1/sns")
public class SNSListenerController {

    private static final Logger log = LoggerFactory.getLogger(SNSListenerController.class);

    /** Tipo de mensaje SNS para confirmación de suscripción. */
    private static final String TYPE_SUBSCRIPTION_CONFIRMATION = "SubscriptionConfirmation";

    /** Tipo de mensaje SNS para confirmación de desuscripción. */
    private static final String TYPE_UNSUBSCRIBE_CONFIRMATION = "UnsubscribeConfirmation";

    /** Tipo de mensaje SNS para notificaciones de eventos. */
    private static final String TYPE_NOTIFICATION = "Notification";

    private final ProcesarEventoTrackingUseCase procesarEventoTrackingUseCase;
    private final SNSMessageValidator snsMessageValidator;
    private final ObjectMapper objectMapper;

    @Autowired
    public SNSListenerController(ProcesarEventoTrackingUseCase procesarEventoTrackingUseCase,
                                  SNSMessageValidator snsMessageValidator,
                                  ObjectMapper objectMapper) {
        this.procesarEventoTrackingUseCase = procesarEventoTrackingUseCase;
        this.snsMessageValidator = snsMessageValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * Endpoint principal que recibe mensajes SNS de tracking de SES.
     *
     * <p>SNS puede enviar el Content-Type como {@code application/json} o {@code text/plain},
     * por lo que se acepta ambos (y cualquier otro tipo con {@code *&#47;*}).</p>
     *
     * @param messageType Tipo de mensaje SNS del header {@code x-amz-sns-message-type}
     * @param payload     Cuerpo del mensaje SNS en formato JSON
     * @return HTTP 200 en caso de éxito, HTTP 403 si la firma es inválida
     */
    @PostMapping(
            value = "/tracking",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, "*/*"}
    )
    public ResponseEntity<String> recibirEventoSNS(
            @RequestHeader(value = "x-amz-sns-message-type", required = false) String messageType,
            @RequestBody String payload) {

        log.info("Mensaje SNS recibido: type={}", messageType);

        try {
            JsonNode snsMessage = objectMapper.readTree(payload);

            // Determinar tipo de mensaje: usar header si está disponible, sino el campo "Type" del JSON
            String type = (messageType != null && !messageType.trim().isEmpty())
                    ? messageType.trim()
                    : (snsMessage.has("Type") ? snsMessage.get("Type").asText() : "");

            if (TYPE_SUBSCRIPTION_CONFIRMATION.equals(type) || TYPE_UNSUBSCRIBE_CONFIRMATION.equals(type)) {
                return manejarSubscriptionConfirmation(snsMessage, payload);
            } else if (TYPE_NOTIFICATION.equals(type)) {
                return manejarNotificacion(snsMessage, payload);
            } else {
                log.warn("Tipo de mensaje SNS desconocido o no especificado: type={}", type);
                // Retornar 200 para evitar reintentos de SNS
                return ResponseEntity.ok("Tipo de mensaje no reconocido, ignorado");
            }

        } catch (SNSSignatureValidationException e) {
            log.error("SEGURIDAD: Firma SNS inválida - posible mensaje fraudulento. Error: {}", e.getMessage());
            return ResponseEntity.status(403).body("Firma SNS inválida");
        } catch (Exception e) {
            log.error("Error inesperado procesando mensaje SNS: {}", e.getMessage(), e);
            // Retornar 200 para evitar reintentos de SNS
            return ResponseEntity.ok("Error procesando mensaje, ignorado");
        }
    }

    /**
     * Maneja mensajes de tipo {@code SubscriptionConfirmation} y {@code UnsubscribeConfirmation}.
     *
     * <p>Extrae el {@code SubscribeURL} del payload y realiza un GET HTTP para confirmar
     * la suscripción con Amazon SNS.</p>
     *
     * @param snsMessage Nodo JSON del mensaje SNS ya parseado
     * @param rawPayload Payload original en texto (para validación de firma)
     * @return HTTP 200 siempre
     */
    private ResponseEntity<String> manejarSubscriptionConfirmation(JsonNode snsMessage, String rawPayload) {
        log.info("Procesando SubscriptionConfirmation/UnsubscribeConfirmation de SNS");

        try {
            // Validar firma del mensaje de confirmación (recomendado por AWS)
            snsMessageValidator.validarFirma(rawPayload);
        } catch (SNSSignatureValidationException e) {
            log.error("SEGURIDAD: Firma inválida en SubscriptionConfirmation SNS: {}", e.getMessage());
            return ResponseEntity.status(403).body("Firma SNS inválida en SubscriptionConfirmation");
        }

        if (!snsMessage.has("SubscribeURL") || snsMessage.get("SubscribeURL").isNull()) {
            log.error("SubscriptionConfirmation SNS sin campo SubscribeURL");
            return ResponseEntity.ok("SubscribeURL ausente, ignorado");
        }

        String subscribeUrl = snsMessage.get("SubscribeURL").asText();
        confirmarSuscripcionSNS(subscribeUrl);

        return ResponseEntity.ok("Suscripción SNS confirmada");
    }

    /**
     * Maneja mensajes de tipo {@code Notification} con eventos de tracking de SES.
     *
     * <p>Flujo:</p>
     * <ol>
     *   <li>Valida la firma del mensaje SNS</li>
     *   <li>Extrae el campo {@code Message} (JSON del evento SES)</li>
     *   <li>Parsea el evento como {@link TrackingEventDTO}</li>
     *   <li>Delega el procesamiento a {@link ProcesarEventoTrackingUseCase}</li>
     * </ol>
     *
     * @param snsMessage Nodo JSON del mensaje SNS ya parseado
     * @param rawPayload Payload original en texto (para validación de firma)
     * @return HTTP 200 siempre (incluso en errores de procesamiento para evitar reintentos)
     */
    private ResponseEntity<String> manejarNotificacion(JsonNode snsMessage, String rawPayload) {
        log.info("Procesando Notification SNS de tracking SES");

        // 1. Validar firma del mensaje SNS
        try {
            snsMessageValidator.validarFirma(rawPayload);
        } catch (SNSSignatureValidationException e) {
            log.error("SEGURIDAD: Firma SNS inválida en Notification: {}", e.getMessage());
            return ResponseEntity.status(403).body("Firma SNS inválida");
        }

        // 2. Extraer el campo Message (contiene el JSON del evento SES)
        if (!snsMessage.has("Message") || snsMessage.get("Message").isNull()) {
            log.error("Notification SNS sin campo Message");
            return ResponseEntity.ok("Message ausente, ignorado");
        }

        String messageJson = snsMessage.get("Message").asText();
        log.debug("Contenido del campo Message SNS: {}", messageJson);

        // 3. Parsear el evento de tracking SES
        TrackingEventDTO trackingEvent;
        try {
            trackingEvent = objectMapper.readValue(messageJson, TrackingEventDTO.class);
        } catch (Exception e) {
            log.error("Error parseando evento de tracking SES desde Message SNS: {}", e.getMessage(), e);
            // Retornar 200 para evitar reintentos de SNS
            return ResponseEntity.ok("Error parseando evento, ignorado");
        }

        log.info("Evento de tracking SES recibido: eventType={}", trackingEvent.getEventType());

        // 4. Delegar procesamiento al caso de uso
        try {
            procesarEventoTrackingUseCase.ejecutar(trackingEvent);
            log.info("Evento de tracking procesado exitosamente: eventType={}", trackingEvent.getEventType());
        } catch (TrackingException e) {
            log.error("Error procesando evento de tracking: eventType={}, error={}",
                    trackingEvent.getEventType(), e.getMessage(), e);
            // Retornar 200 para evitar reintentos de SNS (el error ya fue registrado)
        } catch (Exception e) {
            log.error("Error inesperado procesando evento de tracking: eventType={}, error={}",
                    trackingEvent.getEventType(), e.getMessage(), e);
            // Retornar 200 para evitar reintentos de SNS
        }

        return ResponseEntity.ok("Evento procesado");
    }

    /**
     * Confirma la suscripción SNS realizando un GET HTTP al {@code SubscribeURL}.
     *
     * <p>Amazon SNS requiere que el endpoint confirme la suscripción accediendo
     * a la URL proporcionada en el mensaje de confirmación.</p>
     *
     * @param subscribeUrl URL de confirmación proporcionada por SNS
     */
    private void confirmarSuscripcionSNS(String subscribeUrl) {
        log.info("Confirmando suscripción SNS: url={}", subscribeUrl);
        try {
            URL url = new URL(subscribeUrl);
            try (InputStream response = url.openStream()) {
                // Leer la respuesta para completar la confirmación
                byte[] buffer = new byte[1024];
                while (response.read(buffer) != -1) {
                    // Consumir la respuesta
                }
            }
            log.info("Suscripción SNS confirmada exitosamente: url={}", subscribeUrl);
        } catch (Exception e) {
            log.error("Error confirmando suscripción SNS: url={}, error={}", subscribeUrl, e.getMessage(), e);
            // No relanzar - retornar 200 de todas formas para evitar reintentos
        }
    }
}
