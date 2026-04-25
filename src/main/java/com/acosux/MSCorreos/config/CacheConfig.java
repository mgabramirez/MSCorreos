package com.acosux.MSCorreos.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuración de cache usando Caffeine.
 * 
 * Configura el cache "listaNegraCache" con:
 * - TTL: 5 minutos (expireAfterWrite)
 * - Tamaño máximo: 1000 entradas
 * 
 * Este cache se usa en GestionarListaNegraUseCase.estaEnListaNegra()
 * para optimizar consultas frecuentes de verificación de bloqueo.
 * 
 * Requirements: 6.6
 * 
 * @author MSCorreos Team
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    /**
     * Configura el CacheManager con Caffeine.
     * 
     * Cache "listaNegraCache":
     * - maximumSize: 1000 entradas (evita consumo excesivo de memoria)
     * - expireAfterWrite: 5 minutos (TTL para garantizar datos actualizados)
     * - recordStats: habilita estadísticas de cache para monitoreo
     * 
     * @return CacheManager configurado con Caffeine
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("listaNegraCache");
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }
    
    /**
     * Construye el builder de Caffeine con la configuración específica.
     * 
     * @return Caffeine builder configurado
     */
    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats();
    }
}
