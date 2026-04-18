package com.acosux.MSCorreos.dtos;

import java.io.Serializable;

/**
 * DTO para eventos de tracking recibidos desde Amazon SNS
 * Representa el mensaje completo del evento SNS
 */
public class TrackingEventDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String eventType; // Send, Delivery, Open, Bounce, Complaint, Click
    private MailInfo mail;
    private DeliveryInfo delivery;
    private BounceInfo bounce;
    private ComplaintInfo complaint;
    private OpenInfo open;
    private ClickInfo click;
    
    public TrackingEventDTO() {
    }
    
    // Getters y Setters
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public MailInfo getMail() {
        return mail;
    }
    
    public void setMail(MailInfo mail) {
        this.mail = mail;
    }
    
    public DeliveryInfo getDelivery() {
        return delivery;
    }
    
    public void setDelivery(DeliveryInfo delivery) {
        this.delivery = delivery;
    }
    
    public BounceInfo getBounce() {
        return bounce;
    }
    
    public void setBounce(BounceInfo bounce) {
        this.bounce = bounce;
    }
    
    public ComplaintInfo getComplaint() {
        return complaint;
    }
    
    public void setComplaint(ComplaintInfo complaint) {
        this.complaint = complaint;
    }
    
    public OpenInfo getOpen() {
        return open;
    }
    
    public void setOpen(OpenInfo open) {
        this.open = open;
    }
    
    public ClickInfo getClick() {
        return click;
    }
    
    public void setClick(ClickInfo click) {
        this.click = click;
    }
}
