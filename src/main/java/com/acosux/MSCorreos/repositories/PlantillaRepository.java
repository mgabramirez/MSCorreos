/*
 * Repository interface for Plantilla entity
 * Provides query methods for email template management (future functionality)
 */
package com.acosux.MSCorreos.repositories;

import com.acosux.MSCorreos.entidades.Plantilla;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing Plantilla entities.
 * Provides methods for querying email templates by empresa and tipo_notificacion.
 * 
 * Note: This is for future functionality - email template management.
 * 
 * Requirements: 6.7
 * 
 * @author MSCorreos Team
 */
@Repository
public interface PlantillaRepository extends JpaRepository<Plantilla, Long> {
    
    /**
     * Find a template by empresa and tipo_notificacion
     * This is the primary method for retrieving templates for email generation
     * 
     * @param empresa Company identifier
     * @param tipoNotificacion Notification type
     * @return Optional containing the template if found
     */
    Optional<Plantilla> findByEmpresaAndTipoNotificacion(
        String empresa, 
        String tipoNotificacion
    );
    
    /**
     * Find all templates for a specific empresa
     * 
     * @param empresa Company identifier
     * @return List of templates for the empresa
     */
    List<Plantilla> findByEmpresa(String empresa);
    
    /**
     * Find all templates for a specific tipo_notificacion
     * 
     * @param tipoNotificacion Notification type
     * @return List of templates for the notification type
     */
    List<Plantilla> findByTipoNotificacion(String tipoNotificacion);
    
    /**
     * Find templates by empresa with pagination
     * 
     * @param empresa Company identifier
     * @param pageable Pagination configuration
     * @return Page of templates for the empresa
     */
    Page<Plantilla> findByEmpresa(String empresa, Pageable pageable);
    
    /**
     * Find templates modified after a specific date
     * Useful for tracking recent template changes
     * 
     * @param fecha Date threshold
     * @return List of templates modified after the date
     */
    List<Plantilla> findByFechaModificacionAfter(Date fecha);
    
    /**
     * Find templates created within a date range
     * 
     * @param fechaInicio Start date
     * @param fechaFin End date
     * @param pageable Pagination configuration
     * @return Page of templates created within date range
     */
    Page<Plantilla> findByFechaCreacionBetween(
        Date fechaInicio, 
        Date fechaFin, 
        Pageable pageable
    );
    
    /**
     * Count templates by empresa
     * 
     * @param empresa Company identifier
     * @return Count of templates for the empresa
     */
    Long countByEmpresa(String empresa);
    
    /**
     * Check if a template exists for empresa and tipo_notificacion
     * 
     * @param empresa Company identifier
     * @param tipoNotificacion Notification type
     * @return true if template exists, false otherwise
     */
    boolean existsByEmpresaAndTipoNotificacion(String empresa, String tipoNotificacion);
    
    /**
     * Find all templates ordered by empresa and tipo_notificacion
     * 
     * @param pageable Pagination configuration
     * @return Page of all templates ordered
     */
    @Query("SELECT p FROM Plantilla p ORDER BY p.empresa, p.tipoNotificacion")
    Page<Plantilla> findAllOrdered(Pageable pageable);
    
    /**
     * Find templates by empresa and tipo_notificacion pattern (partial match)
     * 
     * @param empresa Company identifier
     * @param tipoNotificacionPattern Notification type pattern (e.g., "VENTA%")
     * @return List of templates matching the pattern
     */
    @Query("SELECT p FROM Plantilla p " +
           "WHERE p.empresa = :empresa " +
           "AND p.tipoNotificacion LIKE :tipoNotificacionPattern")
    List<Plantilla> findByEmpresaAndTipoNotificacionPattern(
        @Param("empresa") String empresa,
        @Param("tipoNotificacionPattern") String tipoNotificacionPattern
    );
}
