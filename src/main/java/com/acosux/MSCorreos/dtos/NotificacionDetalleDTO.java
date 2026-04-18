package com.acosux.MSCorreos.dtos;

/**
 * DTO para detalle completo de notificación incluyendo JSON del evento
 */
public class NotificacionDetalleDTO extends NotificacionDTO {
    private static final long serialVersionUID = 1L;
    
    private String informeJson; // JSON completo del evento SNS
    
    public NotificacionDetalleDTO() {
        super();
    }
    
    // Getters y Setters
    
    public String getInformeJson() {
        return informeJson;
    }
    
    public void setInformeJson(String informeJson) {
        this.informeJson = informeJson;
    }
}
