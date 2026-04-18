package com.acosux.MSCorreos.infrastructure.exceptions;

/**
 * Excepción lanzada cuando no se encuentra una notificación específica.
 */
public class NotificacionNotFoundException extends Exception {
    
    public NotificacionNotFoundException(String message) {
        super(message);
    }
    
    public NotificacionNotFoundException(Integer id) {
        super("Notificación no encontrada con ID: " + id);
    }
}
