package com.acosux.MSCorreos.dtos;

import java.io.Serializable;
import java.util.List;

/**
 * DTO para headers comunes del correo
 */
public class CommonHeaders implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<String> to;
    private String from;
    private List<String> replyTo;
    private String subject;
    private String messageId;
    private String date;
    
    public CommonHeaders() {
    }
    
    // Getters y Setters
    
    public List<String> getTo() {
        return to;
    }
    
    public void setTo(List<String> to) {
        this.to = to;
    }
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String from) {
        this.from = from;
    }
    
    public List<String> getReplyTo() {
        return replyTo;
    }
    
    public void setReplyTo(List<String> replyTo) {
        this.replyTo = replyTo;
    }
    
    public String getSubject() {
        return subject;
    }
    
    public void setSubject(String subject) {
        this.subject = subject;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public String getDate() {
        return date;
    }
    
    public void setDate(String date) {
        this.date = date;
    }
}
