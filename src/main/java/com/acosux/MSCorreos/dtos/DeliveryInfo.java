package com.acosux.MSCorreos.dtos;

import java.io.Serializable;
import java.util.List;

/**
 * DTO para información de entrega (delivery)
 */
public class DeliveryInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String timestamp;
    private Long processingTimeMillis;
    private List<String> recipients;
    private String smtpResponse;
    private String reportingMTA;
    
    public DeliveryInfo() {
    }
    
    // Getters y Setters
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public Long getProcessingTimeMillis() {
        return processingTimeMillis;
    }
    
    public void setProcessingTimeMillis(Long processingTimeMillis) {
        this.processingTimeMillis = processingTimeMillis;
    }
    
    public List<String> getRecipients() {
        return recipients;
    }
    
    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }
    
    public String getSmtpResponse() {
        return smtpResponse;
    }
    
    public void setSmtpResponse(String smtpResponse) {
        this.smtpResponse = smtpResponse;
    }
    
    public String getReportingMTA() {
        return reportingMTA;
    }
    
    public void setReportingMTA(String reportingMTA) {
        this.reportingMTA = reportingMTA;
    }
}
