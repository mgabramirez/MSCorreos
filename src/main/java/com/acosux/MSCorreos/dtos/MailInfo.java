package com.acosux.MSCorreos.dtos;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DTO para información del correo en eventos SNS
 */
public class MailInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String timestamp;
    private String messageId;
    private String source;
    private String sourceArn;
    private String sendingAccountId;
    private List<String> destination;
    private CommonHeaders commonHeaders;
    private Map<String, List<String>> tags; // Tags de SES: ows-empresa, ows-ruc, ows-clave, etc.
    
    public MailInfo() {
    }
    
    // Getters y Setters
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getSourceArn() {
        return sourceArn;
    }
    
    public void setSourceArn(String sourceArn) {
        this.sourceArn = sourceArn;
    }
    
    public String getSendingAccountId() {
        return sendingAccountId;
    }
    
    public void setSendingAccountId(String sendingAccountId) {
        this.sendingAccountId = sendingAccountId;
    }
    
    public List<String> getDestination() {
        return destination;
    }
    
    public void setDestination(List<String> destination) {
        this.destination = destination;
    }
    
    public CommonHeaders getCommonHeaders() {
        return commonHeaders;
    }
    
    public void setCommonHeaders(CommonHeaders commonHeaders) {
        this.commonHeaders = commonHeaders;
    }
    
    public Map<String, List<String>> getTags() {
        return tags;
    }
    
    public void setTags(Map<String, List<String>> tags) {
        this.tags = tags;
    }
}
