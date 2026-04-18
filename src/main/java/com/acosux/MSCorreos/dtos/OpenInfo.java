package com.acosux.MSCorreos.dtos;

import java.io.Serializable;

/**
 * DTO para información de apertura de correo (open)
 */
public class OpenInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String timestamp;
    private String userAgent;
    private String ipAddress;
    
    public OpenInfo() {
    }
    
    // Getters y Setters
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
