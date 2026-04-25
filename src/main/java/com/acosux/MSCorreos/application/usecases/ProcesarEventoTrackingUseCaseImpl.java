package com.acosux.MSCorreos.application.usecases;

import com.acosux.MSCorreos.dtos.BouncedRecipient;
import com.acosux.MSCorreos.dtos.ComplainedRecipient;
import com.acosux.MSCorreos.dtos.TrackingEventDTO;
import com.acosux.MSCorreos.entidades.CorreosNotificaciones;
import com.acosux.MSCorreos.entidades.TipoBloqueo;
import com.acosux.MSCorreos.enums.TipoEvento;
import com.acosux.MSCorreos.infrastructure.exceptions.TrackingException;
import com.acosux.MSCorreos.repositories.NotificacionesRepository;
import com.acosux.MSCorreos.service.BlacklistService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Implementación del caso de uso para procesar eventos de tracking recibidos desde Amazon SNS.
 * 
 * Este caso de uso es responsable de:
 * - Extraer metadatos de tags (empresa, ruc, clave, tipo_notificacion)
 * - Registrar evento en cor_notificaciones según tipo (Send, Delivery, Open, Bounce, Complaint)
 * - Para Bounce Permanent: agregar a lista negra automáticamente con tipo HARD_BOUNCE
 * - Para Complaint: agregar a lista negra automáticamente con tipo COMPLAINT
 * - Para Soft Bounce: incrementar contador y bloquear si >= 3 en 30 días
 * 
 * Requirements: 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 6.2, 6.3, 6.4, 6.5
 * 
 * @author MSCorreos Team
 */
@Service
@Transactional
public class ProcesarEventoTrackingUseCaseImpl implements ProcesarEventoTrackingUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(ProcesarEventoTrackingUseCaseImpl.class);
    
    private final NotificacionesRepository notificacionesRepository;
    private final BlacklistService blacklistService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public ProcesarEventoTrackingUseCaseImpl(
            NotificacionesRepository notificacionesRepository,
            BlacklistService blacklistService) {
        this.notificacionesRepository = notificacionesRepository;
        this.blacklistService = blacklistService;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Procesa un evento de tracking recibido desde Amazon SNS.
     * 
     * @param evento Evento de tracking parseado desde el mensaje SNS
     * @throws TrackingException si hay error en el procesamiento del evento
     */
    @Override
    public void ejecutar(TrackingEventDTO evento) throws TrackingException {
        if (evento == null) {
            throw new TrackingException("Evento de tracking es nulo");
        }
        
        if (evento.getMail() == null) {
            throw new TrackingException("Información de correo (mail) es nula en el evento");
        }
        
        String eventType = evento.getEventType();
        if (eventType == null || eventType.trim().isEmpty()) {
            throw new TrackingException("Tipo de evento es nulo o vacío");
        }
        
        log.info("Procesando evento de tracking: tipo={}, messageId={}", 
                eventType, evento.getMail().getMessageId());
        
        try {
            // Extraer metadatos de tags
            Map<String, String> metadatos = extraerMetadatos(evento);
            
            // Procesar según tipo de evento
            switch (eventType.toLowerCase()) {
                case "send":
                    procesarEventoSend(evento, metadatos);
                    break;
                case "delivery":
                    procesarEventoDelivery(evento, metadatos);
                    break;
                case "open":
                    procesarEventoOpen(evento, metadatos);
                    break;
                case "bounce":
                    procesarEventoBounce(evento, metadatos);
                    break;
                case "complaint":
                    procesarEventoComplaint(evento, metadatos);
                    break;
                case "click":
                    procesarEventoClick(evento, metadatos);
                    break;
                default:
                    log.warn("Tipo de evento no reconocido: {}", eventType);
                    throw new TrackingException("Tipo de evento no reconocido: " + eventType);
            }
            
            log.info("Evento de tracking procesado exitosamente: tipo={}", eventType);
            
        } catch (Exception e) {
            log.error("Error procesando evento de tracking: tipo={}", eventType, e);
            throw new TrackingException("Error procesando evento de tracking", e);
        }
    }
    
    /**
     * Extrae metadatos de los tags del mensaje SNS.
     * Tags esperados: ows-empresa, ows-ruc, ows-clave, ows-tipo-notificacion
     * 
     * @param evento Evento de tracking
     * @return Map con metadatos extraídos
     */
    private Map<String, String> extraerMetadatos(TrackingEventDTO evento) {
        Map<String, String> metadatos = new java.util.HashMap<>();
        
        if (evento.getMail() != null && evento.getMail().getTags() != null) {
            Map<String, List<String>> tags = evento.getMail().getTags();
            
            // Extraer empresa
            if (tags.containsKey("ows-empresa") && !tags.get("ows-empresa").isEmpty()) {
                metadatos.put("empresa", tags.get("ows-empresa").get(0));
            }
            
            // Extraer RUC
            if (tags.containsKey("ows-ruc") && !tags.get("ows-ruc").isEmpty()) {
                metadatos.put("ruc", tags.get("ows-ruc").get(0));
            }
            
            // Extraer clave
            if (tags.containsKey("ows-clave") && !tags.get("ows-clave").isEmpty()) {
                metadatos.put("clave", tags.get("ows-clave").get(0));
            }
            
            // Extraer tipo de notificación
            if (tags.containsKey("ows-tipo-notificacion") && !tags.get("ows-tipo-notificacion").isEmpty()) {
                metadatos.put("tipo_notificacion", tags.get("ows-tipo-notificacion").get(0));
            }
        }
        
        // Validar que al menos empresa y tipo_notificacion estén presentes
        if (!metadatos.containsKey("empresa") || metadatos.get("empresa").isEmpty()) {
            log.warn("Tag 'ows-empresa' no encontrado en el evento");
            metadatos.put("empresa", "DESCONOCIDO");
        }
        
        if (!metadatos.containsKey("tipo_notificacion") || metadatos.get("tipo_notificacion").isEmpty()) {
            log.warn("Tag 'ows-tipo-notificacion' no encontrado en el evento");
            metadatos.put("tipo_notificacion", "DESCONOCIDO");
        }
        
        return metadatos;
    }
    
    /**
     * Procesa evento de tipo Send (correo enviado).
     * Requirement: 5.3
     */
    private void procesarEventoSend(TrackingEventDTO evento, Map<String, String> metadatos) {
        List<String> destinatarios = evento.getMail().getDestination();
        
        if (destinatarios == null || destinatarios.isEmpty()) {
            log.warn("No hay destinatarios en el evento Send");
            return;
        }
        
        for (String destinatario : destinatarios) {
            registrarNotificacion(
                    destinatario,
                    TipoEvento.SEND.getDescripcion(),
                    null,
                    evento,
                    metadatos
            );
        }
    }
    
    /**
     * Procesa evento de tipo Delivery (correo entregado).
     * Requirement: 5.4
     */
    private void procesarEventoDelivery(TrackingEventDTO evento, Map<String, String> metadatos) {
        List<String> destinatarios = evento.getMail().getDestination();
        
        if (destinatarios == null || destinatarios.isEmpty()) {
            log.warn("No hay destinatarios en el evento Delivery");
            return;
        }
        
        for (String destinatario : destinatarios) {
            registrarNotificacion(
                    destinatario,
                    TipoEvento.DELIVERY.getDescripcion(),
                    null,
                    evento,
                    metadatos
            );
        }
    }
    
    /**
     * Procesa evento de tipo Open (correo abierto).
     * Requirement: 5.5
     */
    private void procesarEventoOpen(TrackingEventDTO evento, Map<String, String> metadatos) {
        List<String> destinatarios = evento.getMail().getDestination();
        
        if (destinatarios == null || destinatarios.isEmpty()) {
            log.warn("No hay destinatarios en el evento Open");
            return;
        }
        
        for (String destinatario : destinatarios) {
            registrarNotificacion(
                    destinatario,
                    TipoEvento.OPEN.getDescripcion(),
                    null,
                    evento,
                    metadatos
            );
        }
    }
    
    /**
     * Procesa evento de tipo Click (link clickeado).
     * Requirement: 5.6
     */
    private void procesarEventoClick(TrackingEventDTO evento, Map<String, String> metadatos) {
        List<String> destinatarios = evento.getMail().getDestination();
        
        if (destinatarios == null || destinatarios.isEmpty()) {
            log.warn("No hay destinatarios en el evento Click");
            return;
        }
        
        for (String destinatario : destinatarios) {
            registrarNotificacion(
                    destinatario,
                    "Click",
                    null,
                    evento,
                    metadatos
            );
        }
    }
    
    /**
     * Procesa evento de tipo Bounce (rebote).
     * Requirements: 5.7, 6.2, 6.4, 6.5
     * 
     * - Para Bounce Permanent: agregar a lista negra con tipo HARD_BOUNCE
     * - Para Bounce Transient: incrementar contador y bloquear si >= 3 en 30 días
     */
    private void procesarEventoBounce(TrackingEventDTO evento, Map<String, String> metadatos) {
        if (evento.getBounce() == null) {
            log.warn("Información de bounce es nula en el evento");
            return;
        }
        
        String bounceType = evento.getBounce().getBounceType();
        List<BouncedRecipient> bouncedRecipients = evento.getBounce().getBouncedRecipients();
        
        if (bouncedRecipients == null || bouncedRecipients.isEmpty()) {
            log.warn("No hay destinatarios rebotados en el evento Bounce");
            return;
        }
        
        TipoEvento tipoEvento = TipoEvento.fromBounceType(bounceType);
        
        for (BouncedRecipient recipient : bouncedRecipients) {
            String email = recipient.getEmailAddress();
            
            if (email == null || email.trim().isEmpty()) {
                log.warn("Email del destinatario rebotado es nulo o vacío");
                continue;
            }
            
            // Registrar notificación
            String observacion = String.format("Bounce Type: %s, Diagnostic: %s", 
                    bounceType, 
                    recipient.getDiagnosticCode() != null ? recipient.getDiagnosticCode() : "N/A");
            
            registrarNotificacion(
                    email,
                    tipoEvento.getDescripcion(),
                    observacion,
                    evento,
                    metadatos
            );
            
            // Gestionar lista negra según tipo de bounce
            if (tipoEvento == TipoEvento.BOUNCE_PERMANENT) {
                // Requirement 6.2: Agregar a lista negra automáticamente con tipo HARD_BOUNCE
                blacklistService.agregarAListaNegra(
                        email,
                        String.format("Hard Bounce automático: %s", bounceType),
                        TipoBloqueo.HARD_BOUNCE,
                        null
                );
            } else if (tipoEvento == TipoEvento.BOUNCE_TRANSIENT) {
                // Requirement 6.4, 6.5: Incrementar contador y bloquear si >= 3 en 30 días
                blacklistService.incrementarSoftBounce(email, bounceType);
            }
        }
    }
    
    /**
     * Procesa evento de tipo Complaint (queja de spam).
     * Requirements: 5.8, 6.3
     * 
     * Agrega automáticamente el email a la lista negra con tipo COMPLAINT.
     */
    private void procesarEventoComplaint(TrackingEventDTO evento, Map<String, String> metadatos) {
        if (evento.getComplaint() == null) {
            log.warn("Información de complaint es nula en el evento");
            return;
        }
        
        List<ComplainedRecipient> complainedRecipients = evento.getComplaint().getComplainedRecipients();
        
        if (complainedRecipients == null || complainedRecipients.isEmpty()) {
            log.warn("No hay destinatarios que se quejaron en el evento Complaint");
            return;
        }
        
        String complaintFeedbackType = evento.getComplaint().getComplaintFeedbackType();
        
        for (ComplainedRecipient recipient : complainedRecipients) {
            String email = recipient.getEmailAddress();
            
            if (email == null || email.trim().isEmpty()) {
                log.warn("Email del destinatario que se quejó es nulo o vacío");
                continue;
            }
            
            // Registrar notificación
            String observacion = String.format("Complaint Type: %s", 
                    complaintFeedbackType != null ? complaintFeedbackType : "N/A");
            
            registrarNotificacion(
                    email,
                    TipoEvento.COMPLAINT.getDescripcion(),
                    observacion,
                    evento,
                    metadatos
            );
            
            // Requirement 6.3: Agregar a lista negra automáticamente con tipo COMPLAINT
            blacklistService.agregarAListaNegra(
                    email,
                    String.format("Complaint automático: %s", complaintFeedbackType != null ? complaintFeedbackType : "spam"),
                    TipoBloqueo.COMPLAINT,
                    null
            );
        }
    }
    
    /**
     * Registra una notificación en la tabla cor_notificaciones.
     * 
     * @param destinatario Email del destinatario
     * @param tipo Tipo de evento (Send, Delivery, Open, Bounce, Complaint)
     * @param observacion Observación adicional (opcional)
     * @param evento Evento completo de tracking
     * @param metadatos Metadatos extraídos de tags
     */
    private void registrarNotificacion(
            String destinatario,
            String tipo,
            String observacion,
            TrackingEventDTO evento,
            Map<String, String> metadatos) {
        
        try {
            CorreosNotificaciones notificacion = new CorreosNotificaciones();
            notificacion.setnDestinatario(destinatario);
            notificacion.setnFecha(new Date());
            notificacion.setnTipo(tipo);
            notificacion.setnObservacion(observacion);
            
            // Convertir evento completo a JSON
            String informeJson = objectMapper.writeValueAsString(evento);
            notificacion.setnInforme(informeJson);
            
            // Establecer metadatos
            notificacion.setnEmpresa(metadatos.getOrDefault("empresa", "DESCONOCIDO"));
            notificacion.setnRuc(metadatos.get("ruc"));
            notificacion.setnClave(metadatos.get("clave"));
            notificacion.setnTipoNotificacion(metadatos.getOrDefault("tipo_notificacion", "DESCONOCIDO"));
            
            notificacionesRepository.save(notificacion);
            
            log.debug("Notificación registrada: destinatario={}, tipo={}, empresa={}", 
                    destinatario, tipo, notificacion.getnEmpresa());
            
        } catch (JsonProcessingException e) {
            log.error("Error convirtiendo evento a JSON", e);
            throw new RuntimeException("Error convirtiendo evento a JSON", e);
        } catch (Exception e) {
            log.error("Error registrando notificación", e);
            throw new RuntimeException("Error registrando notificación", e);
        }
    }
}
