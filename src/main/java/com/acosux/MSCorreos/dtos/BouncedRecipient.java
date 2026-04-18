package com.acosux.MSCorreos.dtos;

import java.io.Serializable;

/**
 * DTO para destinatarios que rebotaron
 */
public class BouncedRecipient implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String emailAddress;
    private String diagnosticCode;
    private String status;
    private String action;
    
    public BouncedRecipient() {
    }
    
    // Getters y Setters
    
    public String getEmailAddress() {
        return emailAddress;
    }
    
    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }
    
    public String getDiagnosticCode() {
        return diagnosticCode;
    }
    
    public void setDiagnosticCode(String diagnosticCode) {
        this.diagnosticCode = diagnosticCode;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
}
