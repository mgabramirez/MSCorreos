/*
 * Repository interface for ListaNegraHistorial entity
 * Provides query methods for blacklist history and audit trail
 */
package com.acosux.MSCorreos.repositories;

import com.acosux.MSCorreos.entidades.AccionListaNegra;
import com.acosux.MSCorreos.entidades.ListaNegraHistorial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Repository interface for managing ListaNegraHistorial entities.
 * Provides methods for querying blacklist change history for audit purposes.
 * 
 * Requirements: 6.1, 7.1, 7.2
 * 
 * @author MSCorreos Team
 */
@Repository
public interface ListaNegraHistorialRepository extends JpaRepository<ListaNegraHistorial, Long> {
    
    /**
     * Find history entries for a specific email, ordered by date descending
     * This is the primary method for viewing an email's blacklist history
     * 
     * @param email Email address to query
     * @return List of history entries for the email, most recent first
     */
    List<ListaNegraHistorial> findByEmailOrderByFechaDesc(String email);
    
    /**
     * Find recent history entries (after a specific date), ordered by date descending
     * Useful for monitoring recent blacklist changes
     * 
     * @param fecha Date threshold (e.g., 30 days ago)
     * @return List of history entries since the specified date
     */
    List<ListaNegraHistorial> findByFechaAfterOrderByFechaDesc(Date fecha);
    
    /**
     * Find history entries by accion (AGREGAR or REMOVER)
     * 
     * @param accion Action type
     * @param pageable Pagination configuration
     * @return Page of history entries matching the action
     */
    Page<ListaNegraHistorial> findByAccion(AccionListaNegra accion, Pageable pageable);
    
    /**
     * Find history entries within a date range
     * 
     * @param fechaInicio Start date
     * @param fechaFin End date
     * @param pageable Pagination configuration
     * @return Page of history entries within date range
     */
    Page<ListaNegraHistorial> findByFechaBetween(
        Date fechaInicio, 
        Date fechaFin, 
        Pageable pageable
    );
    
    /**
     * Find history entries by usuario (user who performed the action)
     * 
     * @param usuario Username
     * @param pageable Pagination configuration
     * @return Page of history entries for the specified user
     */
    Page<ListaNegraHistorial> findByUsuario(String usuario, Pageable pageable);
    
    /**
     * Find history entries for a specific email with pagination
     * 
     * @param email Email address
     * @param pageable Pagination configuration
     * @return Page of history entries for the email
     */
    Page<ListaNegraHistorial> findByEmail(String email, Pageable pageable);
    
    /**
     * Count history entries for a specific email
     * 
     * @param email Email address
     * @return Count of history entries
     */
    Long countByEmail(String email);
    
    /**
     * Find all automatic actions (usuario is null)
     * These are actions triggered by the system automatically
     * 
     * @param pageable Pagination configuration
     * @return Page of automatic history entries
     */
    @Query("SELECT h FROM ListaNegraHistorial h WHERE h.usuario IS NULL ORDER BY h.fecha DESC")
    Page<ListaNegraHistorial> findAutomaticActions(Pageable pageable);
    
    /**
     * Find all manual actions (usuario is not null)
     * These are actions performed by administrators
     * 
     * @param pageable Pagination configuration
     * @return Page of manual history entries
     */
    @Query("SELECT h FROM ListaNegraHistorial h WHERE h.usuario IS NOT NULL ORDER BY h.fecha DESC")
    Page<ListaNegraHistorial> findManualActions(Pageable pageable);
    
    /**
     * Count actions by type within a date range
     * Useful for reporting and analytics
     * 
     * @param accion Action type
     * @param fechaInicio Start date
     * @param fechaFin End date
     * @return Count of actions
     */
    @Query("SELECT COUNT(h) FROM ListaNegraHistorial h " +
           "WHERE h.accion = :accion " +
           "AND h.fecha BETWEEN :fechaInicio AND :fechaFin")
    Long countByAccionAndFechaBetween(
        @Param("accion") AccionListaNegra accion,
        @Param("fechaInicio") Date fechaInicio,
        @Param("fechaFin") Date fechaFin
    );
}
