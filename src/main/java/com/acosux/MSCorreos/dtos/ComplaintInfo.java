package com.acosux.MSCorreos.dtos;

import java.io.Serializable;
import java.util.List;

/**
 * DTO para información de quejas (complaints)
 */
public class ComplaintInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<ComplainedRecipient> complainedRecipients;
    private String timestamp;
    private String complaintFeedbackType; // abuse, fraud, virus, not-spam, etc.
    private String feedbackId;
    private String userAgent;
    private String arrivalDate;
    
    public ComplaintInfo() {
    }
    
    // Getters y Setters
    
    public List<ComplainedRecipient> getComplainedRecipients() {
        return complainedRecipients;
    }
    
    public void setComplainedRecipients(List<ComplainedRecipient> complainedRecipients) {
        this.complainedRecipients = complainedRecipients;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getComplaintFeedbackType() {
        return complaintFeedbackType;
    }
    
    public void setComplaintFeedbackType(String complaintFeedbackType) {
        this.complaintFeedbackType = complaintFeedbackType;
    }
    
    public String getFeedbackId() {
        return feedbackId;
    }
    
    public void setFeedbackId(String feedbackId) {
        this.feedbackId = feedbackId;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public String getArrivalDate() {
        return arrivalDate;
    }
    
    public void setArrivalDate(String arrivalDate) {
        this.arrivalDate = arrivalDate;
    }
}
