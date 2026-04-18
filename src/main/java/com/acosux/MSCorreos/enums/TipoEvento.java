package com.acosux.MSCorreos.enums;

/**
 * Enumeración que representa los tipos de eventos de tracking de correos electrónicos
 * recibidos desde Amazon SNS.
 * 
 * Estos eventos corresponden a los diferentes estados del ciclo de vida de un correo
 * enviado mediante Amazon SES.
 * 
 * @see <a href="https://docs.aws.amazon.com/ses/latest/dg/event-publishing-retrieving-sns.html">Amazon SES Event Publishing</a>
 */
public enum TipoEvento {
    
    /**
     * Correo enviado exitosamente desde Amazon SES.
     * Este evento confirma que SES aceptó el correo para envío.
     */
    SEND("Send"),
    
    /**
     * Correo entregado exitosamente al servidor de correo del destinatario.
     * Este evento confirma que el servidor receptor aceptó el mensaje.
     */
    DELIVERY("Delivery"),
    
    /**
     * Correo abierto por el destinatario.
     * Requiere que el correo contenga un pixel de tracking.
     */
    OPEN("Open"),
    
    /**
     * Rebote temporal (Soft Bounce).
     * El correo no pudo ser entregado temporalmente (buzón lleno, servidor no disponible).
     * El destinatario puede recibir correos en el futuro.
     */
    BOUNCE_TRANSIENT("Bounce - Transient"),
    
    /**
     * Rebote permanente (Hard Bounce).
     * El correo no puede ser entregado de forma permanente (dirección no existe, dominio inválido).
     * El destinatario debe ser agregado a la lista negra automáticamente.
     */
    BOUNCE_PERMANENT("Bounce - Permanent"),
    
    /**
     * Queja de spam reportada por el destinatario.
     * El destinatario marcó el correo como spam en su cliente de correo.
     * El destinatario debe ser agregado a la lista negra automáticamente.
     */
    COMPLAINT("Complaint"),
    
    /**
     * Correo bloqueado por estar el destinatario en lista negra.
     * Este evento es generado internamente por MSCorreos, no por Amazon SNS.
     */
    BLOCKED("Blocked");
    
    private final String descripcion;
    
    TipoEvento(String descripcion) {
        this.descripcion = descripcion;
    }
    
    /**
     * Obtiene la descripción legible del tipo de evento.
     * 
     * @return Descripción del evento
     */
    public String getDescripcion() {
        return descripcion;
    }
    
    /**
     * Determina si el evento representa un rebote (bounce).
     * 
     * @return true si es un evento de tipo bounce (transient o permanent)
     */
    public boolean isBounce() {
        return this == BOUNCE_TRANSIENT || this == BOUNCE_PERMANENT;
    }
    
    /**
     * Determina si el evento requiere acción automática en la lista negra.
     * 
     * @return true si el evento debe agregar el email a la lista negra automáticamente
     */
    public boolean requiereBloqueoAutomatico() {
        return this == BOUNCE_PERMANENT || this == COMPLAINT;
    }
    
    /**
     * Convierte un tipo de bounce de Amazon SNS al enum correspondiente.
     * 
     * @param bounceType Tipo de bounce de SNS ("Permanent", "Transient", "Undetermined")
     * @return TipoEvento correspondiente
     */
    public static TipoEvento fromBounceType(String bounceType) {
        if (bounceType == null) {
            return BOUNCE_TRANSIENT; // Default para bounces sin tipo específico
        }
        
        switch (bounceType.toLowerCase()) {
            case "permanent":
                return BOUNCE_PERMANENT;
            case "transient":
            case "undetermined":
            default:
                return BOUNCE_TRANSIENT;
        }
    }
    
    /**
     * Convierte un tipo de evento de Amazon SNS al enum correspondiente.
     * 
     * @param snsEventType Tipo de evento de SNS ("Send", "Delivery", "Open", "Bounce", "Complaint")
     * @return TipoEvento correspondiente, o null si no se reconoce
     */
    public static TipoEvento fromSnsEventType(String snsEventType) {
        if (snsEventType == null) {
            return null;
        }
        
        switch (snsEventType.toLowerCase()) {
            case "send":
                return SEND;
            case "delivery":
                return DELIVERY;
            case "open":
                return OPEN;
            case "complaint":
                return COMPLAINT;
            default:
                return null;
        }
    }
}
