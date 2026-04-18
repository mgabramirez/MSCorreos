package com.acosux.MSCorreos.dao;

import com.acosux.MSCorreos.entidades.BlacklistEmail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * DAO para la tabla global blacklist_emails.
 * Esta tabla NO está asociada a ninguna empresa (es global).
 */
public interface BlacklistEmailDao extends GenericDao<BlacklistEmail, Long> {
    
    /**
     * Busca un email en la blacklist por email exacto.
     */
    BlacklistEmail findByEmail(String email);
    
    /**
     * Verifica si existe un email activo en la blacklist.
     */
    boolean existsByEmailAndActivoTrue(String email);
    
    /**
     * Cuenta los emails activos en la blacklist.
     */
    long countByActivoTrue();
    
    /**
     * Lista emails activos con paginación.
     */
    Page<BlacklistEmail> findByActivoTrue(Pageable pageable);
}