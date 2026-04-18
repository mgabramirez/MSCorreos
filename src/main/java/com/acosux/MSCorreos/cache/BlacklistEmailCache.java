package com.acosux.MSCorreos.cache;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Componente de cache para almacenar el estado de blacklist de correos electrónicos.
 * Utiliza Redis como almacenamiento subyacente con un TTL de 24 horas.
 */
@Component
public class BlacklistEmailCache {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final String KEY_PREFIX = "blacklist:email:";
    private static final Duration TTL = Duration.ofHours(24);
    
    /**
     * Obtiene el estado de bloqueo de un email desde la cache.
     * 
     * @param email Email a consultar
     * @return true si está bloqueado, false si no lo está, null si no está en cache
     */
    public Boolean get(String email) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        return value != null ? Boolean.parseBoolean(value) : null;
    }
    
    /**
     * Almacena el estado de bloqueo de un email en la cache.
     * 
     * @param email Email a almacenar
     * @param bloqueado Estado de bloqueo
     */
    public void put(String email, boolean bloqueado) {
        redisTemplate.opsForValue().set(KEY_PREFIX + email, String.valueOf(bloqueado), TTL);
    }
    
    /**
     * Invalida la entrada de cache para un email.
     * 
     * @param email Email a invalidar
     */
    public void invalidate(String email) {
        redisTemplate.delete(KEY_PREFIX + email);
    }
}