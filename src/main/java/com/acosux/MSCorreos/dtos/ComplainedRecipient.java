package com.acosux.MSCorreos.dtos;

import java.io.Serializable;

/**
 * DTO para destinatarios que reportaron queja
 */
public class ComplainedRecipient implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String emailAddress;
    
    public ComplainedRecipient() {
    }
    
    // Getters y Setters
    
    public String getEmailAddress() {
        return emailAddress;
    }
    
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
}
