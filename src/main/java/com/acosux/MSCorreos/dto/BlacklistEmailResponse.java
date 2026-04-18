package com.acosux.MSCorreos.dto;

import java.time.LocalDateTime;

/**
 * DTO de respuesta para emails en blacklist.
 */
public class BlacklistEmailResponse {
    
    private Long id;
    private String email;
    private String razon;
    private LocalDateTime fechaBloqueo;
    private String bloqueadoPor;
    private Boolean activo;
    
    public BlacklistEmailResponse() {}
    
    public BlacklistEmailResponse(Long id, String email, String razon, 
            LocalDateTime fechaBloqueo, String bloqueadoPor, Boolean activo) {
        this.id = id;
        this.email = email;
        this.razon = razon;
        this.fechaBloqueo = fechaBloqueo;
        this.bloqueadoPor = bloqueadoPor;
        this.activo = activo;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getRazon() {
        return razon;
    }
    
    public void setRazon(String razon) {
        this.razon = razon;
    }
    
    public LocalDateTime getFechaBloqueo() {
        return fechaBloqueo;
    }
    
    public void setFechaBloqueo(LocalDateTime fechaBloqueo) {
        this.fechaBloqueo = fechaBloqueo;
    }
    
    public String getBloqueadoPor() {
        return bloqueadoPor;
    }
    
    public void setBloqueadoPor(String bloqueadoPor) {
        this.bloqueadoPor = bloqueadoPor;
    }
    
    public Boolean getActivo() {
        return activo;
    }
    
    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}