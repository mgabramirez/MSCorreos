package com.acosux.MSCorreos.dto;

import java.time.LocalDateTime;

/**
 * DTO de respuesta para verificación de email en blacklist.
 */
public class BlacklistCheckResponse {
    
    private String email;
    private boolean bloqueado;
    private LocalDateTime timestamp;
    
    public BlacklistCheckResponse() {}
    
    public BlacklistCheckResponse(String email, boolean bloqueado, LocalDateTime timestamp) {
        this.email = email;
        this.bloqueado = bloqueado;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public boolean isBloqueado() {
        return bloqueado;
    }
    
    public void setBloqueado(boolean bloqueado) {
        this.bloqueado = bloqueado;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
