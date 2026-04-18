package com.acosux.MSCorreos.dtos;

import java.io.Serializable;
import java.util.Date;

/**
 * DTO para respuestas de API de lista negra
 */
public class ListaNegraDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long id;
    private String email;
    private String motivo;
    private Date fechaRegistro;
    private String tipoBloqueo;
    private Boolean activo;
    private Integer contadorSoftBounce;
    private Date ultimoSoftBounce;
    
    public ListaNegraDTO() {
    }
    
    // Getters y Setters
    
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
    
    public String getMotivo() {
        return motivo;
    }
    
    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
    
    public Date getFechaRegistro() {
        return fechaRegistro;
    }
    
    public void setFechaRegistro(Date fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }
    
    public String getTipoBloqueo() {
        return tipoBloqueo;
    }
    
    public void setTipoBloqueo(String tipoBloqueo) {
        this.tipoBloqueo = tipoBloqueo;
    }
    
    public Boolean getActivo() {
        return activo;
    }
    
    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
    
    public Integer getContadorSoftBounce() {
        return contadorSoftBounce;
    }
    
    public void setContadorSoftBounce(Integer contadorSoftBounce) {
        this.contadorSoftBounce = contadorSoftBounce;
    }
    
    public Date getUltimoSoftBounce() {
        return ultimoSoftBounce;
    }
    
    public void setUltimoSoftBounce(Date ultimoSoftBounce) {
        this.ultimoSoftBounce = ultimoSoftBounce;
    }
}
