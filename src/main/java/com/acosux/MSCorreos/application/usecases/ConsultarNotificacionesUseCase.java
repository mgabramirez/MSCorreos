package com.acosux.MSCorreos.application.usecases;

import com.acosux.MSCorreos.dtos.EstadisticasNotificacionDTO;
import com.acosux.MSCorreos.dtos.FiltrosNotificacion;
import com.acosux.MSCorreos.dtos.NotificacionDTO;
import com.acosux.MSCorreos.dtos.NotificacionDetalleDTO;
import com.acosux.MSCorreos.infrastructure.exceptions.NotificacionNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Caso de uso para consultar notificaciones con filtros y paginación.
 *
 * <p>Este caso de uso es responsable de:</p>
 * <ul>
 *   <li>Aplicar filtros (empresa, ruc, tipo, fechas, destinatario, tipo_notificacion)</li>
 *   <li>Implementar paginación</li>
 *   <li>Retornar DTOs con campos requeridos</li>
 *   <li>Implementar obtenerDetalle() que incluye JSON completo</li>
 *   <li>Retornar métricas agregadas por empresa, tipo_notificacion y tipo de evento</li>
 * </ul>
 *
 * <p>Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6</p>
 */
public interface ConsultarNotificacionesUseCase {

    /**
     * Consulta notificaciones con filtros y paginación.
     *
     * @param filtros  Filtros de búsqueda (empresa, ruc, tipo_notificacion, fechas, destinatario, tipo)
     * @param pageable Configuración de paginación (page, size, sort)
     * @return Página de notificaciones que cumplen con los filtros
     */
    Page<NotificacionDTO> ejecutar(FiltrosNotificacion filtros, Pageable pageable);

    /**
     * Obtiene detalle completo de una notificación específica incluyendo el JSON completo del evento.
     *
     * @param id ID de la notificación (n_secuencial)
     * @return Detalle de la notificación con campo n_informe completo
     * @throws NotificacionNotFoundException si la notificación no existe
     */
    NotificacionDetalleDTO obtenerDetalle(Integer id) throws NotificacionNotFoundException;

    /**
     * Obtiene métricas agregadas de notificaciones.
     *
     * <p>Retorna conteos agrupados por:</p>
     * <ul>
     *   <li>Tipo de evento (Send, Delivery, Open, Bounce, Complaint, etc.)</li>
     *   <li>Empresa</li>
     *   <li>Tipo de notificación</li>
     *   <li>Combinación empresa + tipo_notificacion</li>
     * </ul>
     *
     * @return DTO con métricas agregadas
     */
    EstadisticasNotificacionDTO obtenerEstadisticas();
}
