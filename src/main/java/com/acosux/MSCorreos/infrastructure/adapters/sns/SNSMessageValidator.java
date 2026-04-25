package com.acosux.MSCorreos.infrastructure.adapters.sns;

import com.acosux.MSCorreos.infrastructure.exceptions.SNSSignatureValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URL;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validador de mensajes SNS que verifica la autenticidad de los mensajes
 * recibidos desde Amazon SNS mediante firma digital X.509.
 *
 * <p>El proceso de validación sigue la especificación oficial de AWS:</p>
 * <ol>
 *   <li>Verificar que la URL del certificado pertenece al dominio amazonaws.com y usa HTTPS</li>
 *   <li>Descargar el certificado X.509 (con caché para evitar descargas repetidas)</li>
 *   <li>Construir el string a firmar según el tipo de mensaje (Notification vs SubscriptionConfirmation)</li>
 *   <li>Decodificar la firma Base64 y verificar con SHA1withRSA</li>
 * </ol>
 *
 * <p>Tipos de mensaje soportados:</p>
 * <ul>
 *   <li>{@code Notification}: campos Message, MessageId, Subject (si presente),
 *       Timestamp, TopicArn, Type en orden alfabético</li>
 *   <li>{@code SubscriptionConfirmation} y {@code UnsubscribeConfirmation}: campos
 *       Message, MessageId, SubscribeURL, Timestamp, Token, TopicArn, Type en orden alfabético</li>
 * </ul>
 *
 * <p>Requirements: 13.2, 13.6</p>
 *
 * @author MSCorreos Team
 * @see <a href="https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html">
 *      AWS SNS - Verifying the signatures of Amazon SNS messages</a>
 */
@Component
public class SNSMessageValidator {

    private static final Logger log = LoggerFactory.getLogger(SNSMessageValidator.class);

    /** Algoritmo de firma usado por Amazon SNS. */
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    /** Tipo de certificado X.509. */
    private static final String CERTIFICATE_TYPE = "X.509";

    /**
     * Caché de certificados X.509 descargados, indexados por URL.
     * Evita descargas repetidas del mismo certificado durante el ciclo de vida de la aplicación.
     */
    private final ConcurrentHashMap<String, X509Certificate> certificateCache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // API pública
    // -------------------------------------------------------------------------

    /**
     * Valida la firma de un mensaje SNS recibido como payload JSON.
     *
     * <p>Este método es el punto de entrada principal. Parsea el JSON, extrae
     * los campos necesarios y delega en {@link #validarFirmaInterna(JsonNode)}.</p>
     *
     * @param payload Cuerpo del mensaje SNS en formato JSON
     * @return {@code true} si la firma es válida
     * @throws SNSSignatureValidationException si la firma es inválida o hay un error de validación
     * @throws IllegalArgumentException        si el payload es nulo o vacío
     */
    public boolean validarFirma(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            throw new IllegalArgumentException("El payload del mensaje SNS no puede ser nulo o vacío");
        }

        try {
            JsonNode message = objectMapper.readTree(payload);
            return validarFirmaInterna(message);
        } catch (SNSSignatureValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error parseando payload SNS para validación de firma", e);
            throw new SNSSignatureValidationException("Error parseando payload SNS: " + e.getMessage(), e);
        }
    }

    /**
     * Valida la firma de un mensaje SNS ya parseado como {@link JsonNode}.
     *
     * <p>Útil cuando el mensaje ya fue parseado previamente para evitar doble parseo.</p>
     *
     * @param message Nodo JSON del mensaje SNS
     * @return {@code true} si la firma es válida
     * @throws SNSSignatureValidationException si la firma es inválida o hay un error de validación
     */
    public boolean validarFirmaInterna(JsonNode message) {
        String type = obtenerCampoRequerido(message, "Type");
        String signature = obtenerCampoRequerido(message, "Signature");
        String signingCertURL = obtenerCampoRequerido(message, "SigningCertURL");

        log.debug("Validando firma SNS: type={}, certURL={}", type, signingCertURL);

        // 1. Validar URL del certificado
        validarUrlCertificado(signingCertURL);

        // 2. Obtener certificado (con caché)
        X509Certificate cert = obtenerCertificado(signingCertURL);

        // 3. Construir string a firmar
        String stringToSign = construirStringToSign(message, type);
        log.debug("String to sign construido para tipo={}", type);

        // 4. Verificar firma
        verificarFirma(stringToSign, signature, cert);

        log.info("Firma SNS válida: type={}", type);
        return true;
    }

    // -------------------------------------------------------------------------
    // Validación de URL del certificado
    // -------------------------------------------------------------------------

    /**
     * Valida que la URL del certificado pertenece al dominio amazonaws.com y usa HTTPS.
     *
     * <p>Reglas de validación:</p>
     * <ul>
     *   <li>Debe usar protocolo HTTPS</li>
     *   <li>El host debe terminar en {@code .amazonaws.com}</li>
     *   <li>El host debe comenzar con {@code sns.} (para mayor seguridad)</li>
     * </ul>
     *
     * @param certUrl URL del certificado a validar
     * @throws SNSSignatureValidationException si la URL no cumple los requisitos de seguridad
     */
    void validarUrlCertificado(String certUrl) {
        if (certUrl == null || certUrl.trim().isEmpty()) {
            throw new SNSSignatureValidationException(
                    "URL del certificado SNS es nula o vacía");
        }

        try {
            URL url = new URL(certUrl);

            // Debe usar HTTPS
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                log.error("URL del certificado SNS no usa HTTPS: {}", certUrl);
                throw new SNSSignatureValidationException(
                        "URL del certificado SNS debe usar HTTPS: " + certUrl);
            }

            String host = url.getHost();
            if (host == null) {
                throw new SNSSignatureValidationException(
                        "URL del certificado SNS no tiene host válido: " + certUrl);
            }

            // El host debe terminar en .amazonaws.com
            if (!host.endsWith(".amazonaws.com")) {
                log.error("URL del certificado SNS no pertenece al dominio amazonaws.com: {}", certUrl);
                throw new SNSSignatureValidationException(
                        "URL del certificado SNS debe pertenecer al dominio amazonaws.com: " + certUrl);
            }

            // El host debe comenzar con sns. para mayor seguridad
            if (!host.startsWith("sns.")) {
                log.warn("URL del certificado SNS no comienza con 'sns.': {}", certUrl);
                throw new SNSSignatureValidationException(
                        "URL del certificado SNS debe comenzar con 'sns.': " + certUrl);
            }

            log.debug("URL del certificado SNS válida: {}", certUrl);

        } catch (SNSSignatureValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validando URL del certificado SNS: {}", certUrl, e);
            throw new SNSSignatureValidationException(
                    "URL del certificado SNS inválida: " + certUrl, e);
        }
    }

    // -------------------------------------------------------------------------
    // Descarga y caché de certificados
    // -------------------------------------------------------------------------

    /**
     * Obtiene el certificado X.509 desde la URL especificada.
     *
     * <p>Usa un {@link ConcurrentHashMap} como caché para evitar descargas repetidas.
     * El certificado se descarga solo la primera vez que se solicita para cada URL.</p>
     *
     * @param certUrl URL del certificado X.509
     * @return Certificado X.509 descargado
     * @throws SNSSignatureValidationException si no se puede descargar o parsear el certificado
     */
    X509Certificate obtenerCertificado(String certUrl) {
        return certificateCache.computeIfAbsent(certUrl, url -> {
            log.debug("Descargando certificado SNS (cache miss): {}", url);
            return descargarCertificado(url);
        });
    }

    /**
     * Descarga y parsea el certificado X.509 desde la URL especificada.
     *
     * @param certUrl URL del certificado
     * @return Certificado X.509 parseado
     * @throws SNSSignatureValidationException si hay error en la descarga o parseo
     */
    private X509Certificate descargarCertificado(String certUrl) {
        try {
            URL url = new URL(certUrl);
            try (InputStream certStream = url.openStream()) {
                CertificateFactory cf = CertificateFactory.getInstance(CERTIFICATE_TYPE);
                X509Certificate cert = (X509Certificate) cf.generateCertificate(certStream);
                log.debug("Certificado SNS descargado y cacheado: subject={}", cert.getSubjectDN());
                return cert;
            }
        } catch (Exception e) {
            log.error("Error descargando certificado SNS desde: {}", certUrl, e);
            throw new SNSSignatureValidationException(
                    "No se pudo descargar el certificado SNS desde: " + certUrl, e);
        }
    }

    // -------------------------------------------------------------------------
    // Construcción del string a firmar
    // -------------------------------------------------------------------------

    /**
     * Construye el string a firmar según el tipo de mensaje SNS.
     *
     * <p>Los campos se incluyen en orden alfabético, cada uno en su propia línea
     * con el formato: {@code NombreCampo\nValorCampo\n}</p>
     *
     * <p>Para {@code Notification}:</p>
     * <ul>
     *   <li>Message, MessageId, Subject (si presente), Timestamp, TopicArn, Type</li>
     * </ul>
     *
     * <p>Para {@code SubscriptionConfirmation} y {@code UnsubscribeConfirmation}:</p>
     * <ul>
     *   <li>Message, MessageId, SubscribeURL, Timestamp, Token, TopicArn, Type</li>
     * </ul>
     *
     * @param message Nodo JSON del mensaje SNS
     * @param type    Tipo del mensaje SNS
     * @return String a firmar
     * @throws SNSSignatureValidationException si el tipo de mensaje no es soportado
     * @see <a href="https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html">
     *      AWS SNS Signature Verification</a>
     */
    String construirStringToSign(JsonNode message, String type) {
        if ("Notification".equals(type)) {
            return construirStringToSignNotification(message);
        } else if ("SubscriptionConfirmation".equals(type) || "UnsubscribeConfirmation".equals(type)) {
            return construirStringToSignSubscription(message);
        } else {
            throw new SNSSignatureValidationException(
                    "Tipo de mensaje SNS no soportado para construcción de string to sign: " + type);
        }
    }

    /**
     * Construye el string a firmar para mensajes de tipo {@code Notification}.
     *
     * <p>Campos incluidos en orden alfabético:
     * Message, MessageId, Subject (si presente), Timestamp, TopicArn, Type</p>
     *
     * @param message Nodo JSON del mensaje
     * @return String a firmar
     */
    private String construirStringToSignNotification(JsonNode message) {
        StringBuilder sb = new StringBuilder();

        // Message (requerido)
        agregarCampo(sb, "Message", obtenerCampoRequerido(message, "Message"));

        // MessageId (requerido)
        agregarCampo(sb, "MessageId", obtenerCampoRequerido(message, "MessageId"));

        // Subject (opcional - solo si está presente)
        if (message.has("Subject") && !message.get("Subject").isNull()) {
            agregarCampo(sb, "Subject", message.get("Subject").asText());
        }

        // Timestamp (requerido)
        agregarCampo(sb, "Timestamp", obtenerCampoRequerido(message, "Timestamp"));

        // TopicArn (requerido)
        agregarCampo(sb, "TopicArn", obtenerCampoRequerido(message, "TopicArn"));

        // Type (requerido)
        agregarCampo(sb, "Type", obtenerCampoRequerido(message, "Type"));

        return sb.toString();
    }

    /**
     * Construye el string a firmar para mensajes de tipo
     * {@code SubscriptionConfirmation} y {@code UnsubscribeConfirmation}.
     *
     * <p>Campos incluidos en orden alfabético:
     * Message, MessageId, SubscribeURL, Timestamp, Token, TopicArn, Type</p>
     *
     * @param message Nodo JSON del mensaje
     * @return String a firmar
     */
    private String construirStringToSignSubscription(JsonNode message) {
        StringBuilder sb = new StringBuilder();

        // Message (requerido)
        agregarCampo(sb, "Message", obtenerCampoRequerido(message, "Message"));

        // MessageId (requerido)
        agregarCampo(sb, "MessageId", obtenerCampoRequerido(message, "MessageId"));

        // SubscribeURL (requerido)
        agregarCampo(sb, "SubscribeURL", obtenerCampoRequerido(message, "SubscribeURL"));

        // Timestamp (requerido)
        agregarCampo(sb, "Timestamp", obtenerCampoRequerido(message, "Timestamp"));

        // Token (requerido)
        agregarCampo(sb, "Token", obtenerCampoRequerido(message, "Token"));

        // TopicArn (requerido)
        agregarCampo(sb, "TopicArn", obtenerCampoRequerido(message, "TopicArn"));

        // Type (requerido)
        agregarCampo(sb, "Type", obtenerCampoRequerido(message, "Type"));

        return sb.toString();
    }

    /**
     * Agrega un campo al string builder con el formato {@code NombreCampo\nValorCampo\n}.
     *
     * @param sb    StringBuilder destino
     * @param name  Nombre del campo
     * @param value Valor del campo
     */
    private void agregarCampo(StringBuilder sb, String name, String value) {
        sb.append(name).append('\n');
        sb.append(value).append('\n');
    }

    // -------------------------------------------------------------------------
    // Verificación de firma
    // -------------------------------------------------------------------------

    /**
     * Verifica la firma digital del mensaje usando SHA1withRSA.
     *
     * @param stringToSign  String que fue firmado
     * @param signatureB64  Firma en Base64
     * @param cert          Certificado X.509 con la clave pública
     * @throws SNSSignatureValidationException si la firma es inválida o hay error en la verificación
     */
    void verificarFirma(String stringToSign, String signatureB64, X509Certificate cert) {
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(signatureB64);

            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(cert.getPublicKey());
            sig.update(stringToSign.getBytes("UTF-8"));

            boolean valid = sig.verify(signatureBytes);

            if (!valid) {
                log.error("Verificación de firma SNS fallida: firma no coincide con el contenido del mensaje");
                throw new SNSSignatureValidationException(
                        "Firma SNS inválida: la firma no coincide con el contenido del mensaje");
            }

            log.debug("Firma SNS verificada correctamente");

        } catch (SNSSignatureValidationException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("Error decodificando firma Base64 del mensaje SNS", e);
            throw new SNSSignatureValidationException(
                    "Error decodificando firma Base64 del mensaje SNS: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error verificando firma SNS", e);
            throw new SNSSignatureValidationException(
                    "Error verificando firma SNS: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Obtiene el valor de un campo requerido del mensaje JSON.
     *
     * @param message Nodo JSON del mensaje
     * @param campo   Nombre del campo
     * @return Valor del campo como String
     * @throws SNSSignatureValidationException si el campo no existe o es nulo
     */
    private String obtenerCampoRequerido(JsonNode message, String campo) {
        if (!message.has(campo) || message.get(campo).isNull()) {
            throw new SNSSignatureValidationException(
                    "Campo requerido ausente en mensaje SNS: " + campo);
        }
        return message.get(campo).asText();
    }

    /**
     * Invalida la entrada de caché para una URL de certificado específica.
     *
     * <p>Útil para forzar la re-descarga de un certificado (por ejemplo, si fue renovado).</p>
     *
     * @param certUrl URL del certificado a invalidar en caché
     */
    public void invalidarCacheCertificado(String certUrl) {
        certificateCache.remove(certUrl);
        log.debug("Caché de certificado invalidado para URL: {}", certUrl);
    }
}
