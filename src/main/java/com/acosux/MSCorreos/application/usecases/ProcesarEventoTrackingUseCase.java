package com.acosux.MSCorreos.application.usecases;

import com.acosux.MSCorreos.dtos.TrackingEventDTO;
import com.acosux.MSCorreos.infrastructure.exceptions.TrackingException;

/**
 * Caso de uso para procesar eventos de tracking recibidos desde Amazon SNS.
 * 
 * Este caso de uso es responsable de:
 * - Extraer metadatos de tags (empresa, ruc, clave, tipo_notificacion)
 * - Registrar evento en cor_notificaciones según tipo (Send, Delivery, Open, Bounce, Complaint)
 * - Para Bounce Permanent: agregar a lista negra automáticamente con tipo HARD_BOUNCE
 * - Para Complaint: agregar a lista negra automáticamente con tipo COMPLAINT
 * - Para Soft Bounce: incrementar contador y bloquear si >= 3 en 30 días
 * 
 * Requirements: 5.1, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 6.2, 6.3, 6.4, 6.5
 */
public interface ProcesarEventoTrackingUseCase {
    
    /**
     * Procesa un evento de tracking recibido desde Amazon SNS.
     * 
     * @param evento Evento de tracking parseado desde el mensaje SNS
     * @throws TrackingException si hay error en el procesamiento del evento
     */
    void ejecutar(TrackingEventDTO evento) throws TrackingException;
}
