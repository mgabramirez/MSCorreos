package com.acosux.MSCorreos.dtos;

import java.io.Serializable;
import java.util.List;

/**
 * DTO para información de rebotes (bounces)
 */
public class BounceInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String bounceType; // Permanent, Transient, Undetermined
    private String bounceSubType; // General, NoEmail, Suppressed, OnAccountSuppressionList, etc.
    private List<BouncedRecipient> bouncedRecipients;
    private String timestamp;
    private String feedbackId;
    private String reportingMTA;
    
    public BounceInfo() {
    }
    
    // Getters y Setters
    
    public String getBounceType() {
        return bounceType;
    }
    
    public void setBounceType(String bounceType) {
        this.bounceType = bounceType;
    }
    
    public String getBounceSubType() {
        return bounceSubType;
    }
    
    public void setBounceSubType(String bounceSubType) {
        this.bounceSubType = bounceSubType;
    }
    
    public List<BouncedRecipient> getBouncedRecipients() {
        return bouncedRecipients;
    }
    
    public void setBouncedRecipients(List<BouncedRecipient> bouncedRecipients) {
        this.bouncedRecipients = bouncedRecipients;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getFeedbackId() {
        return feedbackId;
    }
    
    public void setFeedbackId(String feedbackId) {
        this.feedbackId = feedbackId;
    }
    
    public String getReportingMTA() {
        return reportingMTA;
    }
    
    public void setReportingMTA(String reportingMTA) {
        this.reportingMTA = reportingMTA;
    }
}
