package com.acosux.MSCorreos.dtos;

import java.io.Serializable;

/**
 * DTO para solicitud de agregar email a lista negra
 * Validaciones se realizarán en la capa de servicio
 */
public class AgregarListaNegraRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String email;
    private String motivo;
    
    private String tipoBloqueo; // Default: MANUAL
    
    public AgregarListaNegraRequest() {
    }
    
    // Getters y Setters
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getMotivo() {
        return motivo;
    }
    
    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
    
    public String getTipoBloqueo() {
        return tipoBloqueo;
    }
    
    public void setTipoBloqueo(String tipoBloqueo) {
        this.tipoBloqueo = tipoBloqueo;
    }
}
