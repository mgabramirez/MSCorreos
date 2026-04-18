/*
 * Repository interface for ListaNegra entity
 * Provides query methods for blacklist management
 */
package com.acosux.MSCorreos.repositories;

import com.acosux.MSCorreos.entidades.ListaNegra;
import com.acosux.MSCorreos.entidades.TipoBloqueo;
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
 * Repository interface for managing ListaNegra entities.
 * Provides methods for querying and managing the email blacklist.
 * 
 * Requirements: 6.1, 7.1, 7.2
 * 
 * @author MSCorreos Team
 */
@Repository
public interface ListaNegraRepository extends JpaRepository<ListaNegra, Long> {
    
    /**
     * Find an active blacklist entry by email
     * This is the primary method for checking if an email is blacklisted
     * 
     * @param email Email address to check
     * @return Optional containing the blacklist entry if found and active
     */
    Optional<ListaNegra> findByEmailAndActivoTrue(String email);
    
    /**
     * Find a blacklist entry by email (regardless of active status)
     * 
     * @param email Email address to find
     * @return Optional containing the blacklist entry if found
     */
    Optional<ListaNegra> findByEmail(String email);
    
    /**
     * Find all active blacklist entries by tipo_bloqueo
     * 
     * @param tipoBloqueo Type of block (HARD_BOUNCE, SOFT_BOUNCE_REPETIDO, COMPLAINT, MANUAL)
     * @return List of active blacklist entries matching tipo_bloqueo
     */
    List<ListaNegra> findByTipoBloqueoAndActivoTrue(TipoBloqueo tipoBloqueo);
    
    /**
     * Find recent soft bounces (within last 30 days)
     * Used for monitoring and analysis
     * 
     * @param fecha Date threshold (typically 30 days ago)
     * @return List of soft bounce entries since the specified date
     */
    @Query("SELECT ln FROM ListaNegra ln " +
           "WHERE ln.activo = true " +
           "AND ln.tipoBloqueo = 'SOFT_BOUNCE_REPETIDO' " +
           "AND ln.ultimoSoftBounce >= :fecha")
    List<ListaNegra> findSoftBouncesRecientes(@Param("fecha") Date fecha);
    
    /**
     * Count all active blacklist entries
     * 
     * @return Count of active blacklist entries
     */
    Long countByActivoTrue();
    
    /**
     * Find all active blacklist entries with pagination
     * 
     * @param pageable Pagination configuration
     * @return Page of active blacklist entries
     */
    Page<ListaNegra> findByActivoTrue(Pageable pageable);
    
    /**
     * Find blacklist entries by tipo_bloqueo with pagination
     * 
     * @param tipoBloqueo Type of block
     * @param pageable Pagination configuration
     * @return Page of blacklist entries matching tipo_bloqueo
     */
    Page<ListaNegra> findByTipoBloqueo(TipoBloqueo tipoBloqueo, Pageable pageable);
    
    /**
     * Find blacklist entries registered within a date range
     * 
     * @param fechaInicio Start date
     * @param fechaFin End date
     * @param pageable Pagination configuration
     * @return Page of blacklist entries within date range
     */
    Page<ListaNegra> findByFechaRegistroBetween(
        Date fechaInicio, 
        Date fechaFin, 
        Pageable pageable
    );
    
    /**
     * Find all blacklist entries (active and inactive) with pagination
     * 
     * @param pageable Pagination configuration
     * @return Page of all blacklist entries
     */
    Page<ListaNegra> findAll(Pageable pageable);
    
    /**
     * Count blacklist entries by tipo_bloqueo
     * 
     * @param tipoBloqueo Type of block
     * @return Count of entries with specified tipo_bloqueo
     */
    Long countByTipoBloqueo(TipoBloqueo tipoBloqueo);
}
