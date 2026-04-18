/*
 * Repository interface for CorreosNotificaciones entity
 * Provides query methods for notification management
 */
package com.acosux.MSCorreos.repositories;

import com.acosux.MSCorreos.entidades.CorreosNotificaciones;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Repository interface for managing CorreosNotificaciones entities.
 * Provides methods for querying notifications with various filters.
 * 
 * Requirements: 2.1, 7.1, 7.2
 * 
 * @author MSCorreos Team
 */
@Repository
public interface NotificacionesRepository extends JpaRepository<CorreosNotificaciones, Integer>, JpaSpecificationExecutor<CorreosNotificaciones> {
    
    /**
     * Find notifications by empresa and tipo_notificacion within a date range
     * 
     * @param empresa Company identifier
     * @param tipoNotificacion Notification type
     * @param fechaInicio Start date
     * @param fechaFin End date
     * @param pageable Pagination configuration
     * @return Page of notifications matching criteria
     */
    Page<CorreosNotificaciones> findByNEmpresaAndNTipoNotificacionAndNFechaBetween(
        String empresa, 
        String tipoNotificacion, 
        Date fechaInicio, 
        Date fechaFin, 
        Pageable pageable
    );
    
    /**
     * Find notifications by destinatario (partial match)
     * 
     * @param destinatario Email address or partial email
     * @param pageable Pagination configuration
     * @return Page of notifications matching destinatario
     */
    Page<CorreosNotificaciones> findByNDestinatarioContaining(
        String destinatario, 
        Pageable pageable
    );
    
    /**
     * Find notifications by RUC and clave
     * 
     * @param ruc Tax identification number
     * @param clave Composite key (periodo_motivo_numero or sector_motivo_numero)
     * @return List of notifications matching RUC and clave
     */
    List<CorreosNotificaciones> findByNRucAndNClave(String ruc, String clave);
    
    /**
     * Find notifications by event type
     * 
     * @param tipo Event type (Send, Delivery, Open, Bounce, Complaint, etc.)
     * @param pageable Pagination configuration
     * @return Page of notifications matching tipo
     */
    Page<CorreosNotificaciones> findByNTipo(String tipo, Pageable pageable);
    
    /**
     * Count notifications by empresa and tipo_notificacion
     * 
     * @param empresa Company identifier
     * @param tipoNotificacion Notification type
     * @return Count of notifications
     */
    @Query("SELECT COUNT(n) FROM CorreosNotificaciones n " +
           "WHERE n.nEmpresa = :empresa AND n.nTipoNotificacion = :tipoNotificacion")
    Long countByEmpresaAndTipoNotificacion(
        @Param("empresa") String empresa, 
        @Param("tipoNotificacion") String tipoNotificacion
    );
    
    /**
     * Find notifications by empresa with pagination
     * 
     * @param empresa Company identifier
     * @param pageable Pagination configuration
     * @return Page of notifications for the empresa
     */
    Page<CorreosNotificaciones> findByNEmpresa(String empresa, Pageable pageable);
    
    /**
     * Find notifications by empresa and tipo with pagination
     * 
     * @param empresa Company identifier
     * @param tipo Event type
     * @param pageable Pagination configuration
     * @return Page of notifications matching empresa and tipo
     */
    Page<CorreosNotificaciones> findByNEmpresaAndNTipo(
        String empresa, 
        String tipo, 
        Pageable pageable
    );
    
    /**
     * Find notifications within a date range
     * 
     * @param fechaInicio Start date
     * @param fechaFin End date
     * @param pageable Pagination configuration
     * @return Page of notifications within date range
     */
    Page<CorreosNotificaciones> findByNFechaBetween(
        Date fechaInicio, 
        Date fechaFin, 
        Pageable pageable
    );
}
