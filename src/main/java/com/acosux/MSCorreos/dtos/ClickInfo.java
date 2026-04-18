package com.acosux.MSCorreos.dtos;

import java.io.Serializable;

/**
 * DTO para información de clicks en enlaces del correo
 */
public class ClickInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String timestamp;
    private String userAgent;
    private String ipAddress;
    private String link;
    private String linkTags;
    
    public ClickInfo() {
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
    
    public String getLink() {
        return link;
    }
    
    public void setLink(String link) {
        this.link = link;
    }
    
    public String getLinkTags() {
        return linkTags;
    }
    
    public void setLinkTags(String linkTags) {
        this.linkTags = linkTags;
    }
}
