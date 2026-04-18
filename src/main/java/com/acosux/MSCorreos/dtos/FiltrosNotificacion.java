package com.acosux.MSCorreos.dtos;

import java.io.Serializable;
import java.util.Date;

/**
 * DTO para filtros de búsqueda de notificaciones
 */
public class FiltrosNotificacion implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String empresa;
    private String ruc;
    private String tipoNotificacion;
    private Date fechaInicio;
    private Date fechaFin;
    private String destinatario;
    private String tipo;
    
    public FiltrosNotificacion() {
    }
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private FiltrosNotificacion filtros;
        
        public Builder() {
            filtros = new FiltrosNotificacion();
        }
        
        public Builder empresa(String empresa) {
            filtros.empresa = empresa;
            return this;
        }
        
        public Builder ruc(String ruc) {
            filtros.ruc = ruc;
            return this;
        }
        
        public Builder tipoNotificacion(String tipoNotificacion) {
            filtros.tipoNotificacion = tipoNotificacion;
            return this;
        }
        
        public Builder fechaInicio(Date fechaInicio) {
            filtros.fechaInicio = fechaInicio;
            return this;
        }
        
        public Builder fechaFin(Date fechaFin) {
            filtros.fechaFin = fechaFin;
            return this;
        }
        
        public Builder destinatario(String destinatario) {
            filtros.destinatario = destinatario;
            return this;
        }
        
        public Builder tipo(String tipo) {
            filtros.tipo = tipo;
            return this;
        }
        
        public FiltrosNotificacion build() {
            return filtros;
        }
    }
    
    // Getters y Setters
    
    public String getEmpresa() {
        return empresa;
    }
    
    public void setEmpresa(String empresa) {
        this.empresa = empresa;
    }
    
    public String getRuc() {
        return ruc;
    }
    
    public void setRuc(String ruc) {
        this.ruc = ruc;
    }
    
    public String getTipoNotificacion() {
        return tipoNotificacion;
    }
    
    public void setTipoNotificacion(String tipoNotificacion) {
        this.tipoNotificacion = tipoNotificacion;
    }
    
    public Date getFechaInicio() {
        return fechaInicio;
    }
    
    public void setFechaInicio(Date fechaInicio) {
        this.fechaInicio = fechaInicio;
    }
    
    public Date getFechaFin() {
        return fechaFin;
    }
    
    public void setFechaFin(Date fechaFin) {
        this.fechaFin = fechaFin;
    }
    
    public String getDestinatario() {
        return destinatario;
    }
    
    public void setDestinatario(String destinatario) {
        this.destinatario = destinatario;
    }
    
    public String getTipo() {
        return tipo;
    }
    
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
