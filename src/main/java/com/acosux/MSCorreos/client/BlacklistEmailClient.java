package com.acosux.MSCorreos.client;

import com.acosux.MSCorreos.dto.BlacklistCheckResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client para comunicación con el servicio de blacklist de emails.
 * Permite verificar si un email está bloqueado antes de enviar notificaciones.
 */
@FeignClient(name = "blacklist-emails-service", url = "${blacklist.service.url}")
public interface BlacklistEmailClient {
    
    /**
     * Verifica si un email está en la blacklist.
     * 
     * @param email Email a verificar
     * @return BlacklistCheckResponse con el estado del email
     */
    @GetMapping("/api/v1/blacklist-emails/{email}")
    BlacklistCheckResponse checkEmail(@PathVariable("email") String email);
}
