package com.acosux.MSCorreos.enums;

/**
 * Enumeración que representa los tipos de bloqueo en la lista negra de correos.
 * 
 * Define las diferentes razones por las cuales un correo electrónico puede ser
 * bloqueado para evitar envíos futuros y prevenir multas de Amazon SES.
 */
public enum TipoBloqueo {
    
    /**
     * Rebote permanente (Hard Bounce).
     * El correo no puede ser entregado de forma permanente porque:
     * - La dirección de correo no existe
     * - El dominio es inválido
     * - El servidor receptor rechaza permanentemente el correo
     * 
     * Acción: Bloqueo automático inmediato al recibir evento de Amazon SNS.
     */
    HARD_BOUNCE("Hard Bounce - Dirección inválida o inexistente"),
    
    /**
     * Rebotes temporales repetidos (Soft Bounce Repetido).
     * El correo ha generado 3 o más rebotes temporales en un período de 30 días.
     * 
     * Los rebotes temporales pueden ocurrir por:
     * - Buzón lleno
     * - Servidor de correo temporalmente no disponible
     * - Mensaje demasiado grande
     * 
     * Acción: Bloqueo automático al alcanzar el umbral de 3 soft bounces en 30 días.
     */
    SOFT_BOUNCE_REPETIDO("Soft Bounce Repetido - 3+ rebotes temporales en 30 días"),
    
    /**
     * Queja de spam (Complaint).
     * El destinatario marcó el correo como spam en su cliente de correo.
     * 
     * Esto indica que:
     * - El destinatario no desea recibir correos
     * - El contenido puede ser considerado spam
     * - Puede afectar la reputación del remitente
     * 
     * Acción: Bloqueo automático inmediato al recibir evento de Amazon SNS.
     */
    COMPLAINT("Complaint - Marcado como spam por destinatario"),
    
    /**
     * Bloqueo manual por administrador.
     * Un administrador del sistema agregó manualmente el correo a la lista negra.
     * 
     * Razones comunes:
     * - Solicitud explícita del destinatario
     * - Correo identificado como problemático
     * - Decisión administrativa
     * 
     * Acción: Agregado manualmente mediante API REST.
     */
    MANUAL("Manual - Bloqueado por administrador");
    
    private final String descripcion;
    
    TipoBloqueo(String descripcion) {
        this.descripcion = descripcion;
    }
    
    /**
     * Obtiene la descripción legible del tipo de bloqueo.
     * 
     * @return Descripción del tipo de bloqueo
     */
    public String getDescripcion() {
        return descripcion;
    }
    
    /**
     * Determina si el bloqueo fue automático (no manual).
     * 
     * @return true si el bloqueo fue generado automáticamente por el sistema
     */
    public boolean esAutomatico() {
        return this != MANUAL;
    }
    
    /**
     * Determina si el bloqueo es permanente y no debe ser removido automáticamente.
     * 
     * @return true si el bloqueo debe mantenerse indefinidamente
     */
    public boolean esPermanente() {
        return this == HARD_BOUNCE || this == COMPLAINT;
    }
    
    /**
     * Determina si el bloqueo puede expirar después de cierto tiempo.
     * 
     * @return true si el bloqueo puede ser removido automáticamente después de un período
     */
    public boolean puedeExpirar() {
        return this == SOFT_BOUNCE_REPETIDO;
    }
    
    /**
     * Obtiene el tipo de bloqueo correspondiente a un tipo de evento.
     * 
     * @param tipoEvento Tipo de evento que genera el bloqueo
     * @return TipoBloqueo correspondiente, o null si el evento no genera bloqueo
     */
    public static TipoBloqueo fromTipoEvento(TipoEvento tipoEvento) {
        if (tipoEvento == null) {
            return null;
        }
        
        switch (tipoEvento) {
            case BOUNCE_PERMANENT:
                return HARD_BOUNCE;
            case COMPLAINT:
                return COMPLAINT;
            case BOUNCE_TRANSIENT:
                // No se bloquea inmediatamente, se incrementa contador
                return null;
            default:
                return null;
        }
    }
}
