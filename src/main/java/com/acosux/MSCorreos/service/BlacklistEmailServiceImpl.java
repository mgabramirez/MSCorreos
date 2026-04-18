package com.acosux.MSCorreos.service;

import com.acosux.MSCorreos.dao.BlacklistEmailDao;
import com.acosux.MSCorreos.entidades.BlacklistEmail;
import com.acosux.MSCorreos.exception.EmailYaExisteException;
import com.acosux.MSCorreos.exception.EmailNoEncontradoException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación del servicio de blacklist de correos.
 * Implementa el patrón cache-aside para optimizar consultas.
 */
@Service
public class BlacklistEmailServiceImpl implements BlacklistEmailService {
    
    @Autowired
    private BlacklistEmailDao blacklistDao;
    
    // Cache en memoria simple (puede reemplazarse con Redis)
    // Para producción, considerar Redis con el patrón cache-aside
    private final java.util.Map<String, Boolean> cacheLocal = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_TTL_MILLIS = 24 * 60 * 60 * 1000; // 24 horas
    private final java.util.Map<String, Long> cacheTimestamps = new java.util.concurrent.ConcurrentHashMap<>();
    
    @Override
    public boolean isEmailBloqueado(String email) {
        String emailNormalizado = normalizarEmail(email);
        if (emailNormalizado == null) {
            return false;
        }
        
        // 1. Consultar cache local primero
        Boolean cached = getFromCache(emailNormalizado);
        if (cached != null) {
            return cached;
        }
        
        // 2. Consultar base de datos
        boolean bloqueado = blacklistDao.existsByEmailAndActivoTrue(emailNormalizado);
        
        // 3. Actualizar cache
        putInCache(emailNormalizado, bloqueado);
        
        return bloqueado;
    }
    
    @Override
    @Transactional
    public BlacklistEmail agregarEmail(String email, String razon, String usuario) {
        String emailNormalizado = normalizarEmail(email);
        if (emailNormalizado == null) {
            throw new IllegalArgumentException("El email no puede ser nulo o vacío");
        }
        
        // Verificar si ya existe
        BlacklistEmail existente = blacklistDao.findByEmail(emailNormalizado);
        
        if (existente != null) {
            if (existente.getActivo()) {
                throw new EmailYaExisteException("El email ya está en blacklist");
            }
            // Reactivar email existente
            existente.setActivo(true);
            existente.setRazon(razon);
            existente.setBloqueadoPor(usuario);
            existente.setFechaBloqueo(LocalDateTime.now());
            existente.setUsuarioActualizacion(usuario);
            existente.setFechaActualizacion(LocalDateTime.now());
            
            BlacklistEmail guardado = existente;
            blacklistDao.actualizar(guardado);
            
            // Actualizar cache
            putInCache(emailNormalizado, true);
            
            return guardado;
        }
        
        // Crear nuevo registro
        BlacklistEmail nuevo = new BlacklistEmail();
        nuevo.setEmail(emailNormalizado);
        nuevo.setRazon(razon);
        nuevo.setBloqueadoPor(usuario);
        nuevo.setFechaBloqueo(LocalDateTime.now());
        nuevo.setActivo(true);
        nuevo.setUsuarioCreacion(usuario);
        nuevo.setFechaCreacion(LocalDateTime.now());
        
        blacklistDao.insertar(nuevo);
        
        // Actualizar cache
        putInCache(emailNormalizado, true);
        
        return nuevo;
    }
    
    @Override
    @Transactional
    public void eliminarEmail(String email, String usuario) {
        String emailNormalizado = normalizarEmail(email);
        if (emailNormalizado == null) {
            throw new IllegalArgumentException("El email no puede ser nulo o vacío");
        }
        
        BlacklistEmail existente = blacklistDao.findByEmail(emailNormalizado);
        if (existente == null) {
            throw new EmailNoEncontradoException("Email no encontrado en blacklist");
        }
        
        existente.setActivo(false);
        existente.setUsuarioActualizacion(usuario);
        existente.setFechaActualizacion(LocalDateTime.now());
        
        blacklistDao.actualizar(existente);
        
        // Invalidar cache
        invalidateCache(emailNormalizado);
    }
    
    @Override
    public List<BlacklistEmail> listarEmails(int inicio, int cantidad) {
        // Usar HQL para paginación
        String hql = "FROM BlacklistEmail WHERE activo = true ORDER BY fechaBloqueo DESC";
        return blacklistDao.obtenerPorHql(hql, null, inicio, cantidad);
    }
    
    @Override
    public Page<BlacklistEmail> listarEmails(Pageable pageable) {
        return blacklistDao.findByActivoTrue(pageable);
    }
    
    @Override
    public long countEmails() {
        return blacklistDao.countByActivoTrue();
    }
    
    /**
     * Normaliza email: lowercase y trim.
     */
    private String normalizarEmail(String email) {
        return email != null ? email.toLowerCase().trim() : null;
    }
    
    // === Métodos de Cache Local ===
    
    private Boolean getFromCache(String email) {
        Long timestamp = cacheTimestamps.get(email);
        if (timestamp != null) {
            if (System.currentTimeMillis() - timestamp > CACHE_TTL_MILLIS) {
                // Cache expirado
                cacheLocal.remove(email);
                cacheTimestamps.remove(email);
                return null;
            }
            return cacheLocal.get(email);
        }
        return null;
    }
    
    private void putInCache(String email, boolean value) {
        cacheLocal.put(email, value);
        cacheTimestamps.put(email, System.currentTimeMillis());
    }
    
    private void invalidateCache(String email) {
        cacheLocal.remove(email);
        cacheTimestamps.remove(email);
    }
}