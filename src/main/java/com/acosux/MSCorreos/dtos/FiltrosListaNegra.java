package com.acosux.MSCorreos.dtos;

import java.io.Serializable;
import java.util.Date;

/**
 * DTO para filtros de búsqueda de lista negra
 */
public class FiltrosListaNegra implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String tipoBloqueo;
    private Date fechaDesde;
    private Date fechaHasta;
    private Boolean activo;
    
    public FiltrosListaNegra() {
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private FiltrosListaNegra filtros;
        
        public Builder() {
            filtros = new FiltrosListaNegra();
        }
        
        public Builder tipoBloqueo(String tipoBloqueo) {
            filtros.tipoBloqueo = tipoBloqueo;
            return this;
        }
        
        public Builder fechaDesde(Date fechaDesde) {
            filtros.fechaDesde = fechaDesde;
            return this;
        }
        
        public Builder fechaHasta(Date fechaHasta) {
            filtros.fechaHasta = fechaHasta;
            return this;
        }
        
        public Builder activo(Boolean activo) {
            filtros.activo = activo;
            return this;
        }
        
        public FiltrosListaNegra build() {
            return filtros;
        }
    }
    
    // Getters y Setters
    
    public String getTipoBloqueo() {
        return tipoBloqueo;
    }
    
    public void setTipoBloqueo(String tipoBloqueo) {
        this.tipoBloqueo = tipoBloqueo;
    }
    
    public Date getFechaDesde() {
        return fechaDesde;
    }
    
    public void setFechaDesde(Date fechaDesde) {
        this.fechaDesde = fechaDesde;
    }
    
    public Date getFechaHasta() {
        return fechaHasta;
    }
    
    public void setFechaHasta(Date fechaHasta) {
        this.fechaHasta = fechaHasta;
    }
    
    public Boolean getActivo() {
        return activo;
    }
    
    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
