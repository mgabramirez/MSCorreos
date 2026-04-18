package com.acosux.MSCorreos.application.usecases;

import com.acosux.MSCorreos.dtos.FiltrosNotificacion;
import com.acosux.MSCorreos.dtos.NotificacionDTO;
import com.acosux.MSCorreos.dtos.NotificacionDetalleDTO;
import com.acosux.MSCorreos.infrastructure.exceptions.NotificacionNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Caso de uso para consultar notificaciones con filtros y paginación.
 * 
 * Este caso de uso es responsable de:
 * - Aplicar filtros (empresa, ruc, tipo, fechas, destinatario, tipo_notificacion)
 * - Implementar paginación
 * - Retornar DTOs con campos requeridos
 * - Implementar obtenerDetalle() que incluye JSON completo
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */
public interface ConsultarNotificacionesUseCase {
    
    /**
     * Consulta notificaciones con filtros y paginación.
     * 
     * @param filtros Filtros de búsqueda (empresa, ruc, tipo_notificacion, fechas, destinatario, tipo)
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
}
