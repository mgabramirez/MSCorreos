package com.acosux.MSCorreos.infrastructure.exceptions;

/**
 * Excepción lanzada cuando hay un error al procesar un evento de tracking.
 */
public class TrackingException extends Exception {
    
    public TrackingException(String message) {
        super(message);
    }
    
    public TrackingException(String message, Throwable cause) {
        super(message, cause);
    }
}
