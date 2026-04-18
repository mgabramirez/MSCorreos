/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.acosux.MSCorreos.entidades;

/**
 * Enumeración que representa los tipos de bloqueo en la lista negra
 * 
 * @author MSCorreos Team
 */
public enum TipoBloqueo {
    /**
     * Rebote permanente - dirección de correo no existe o dominio inválido
     */
    HARD_BOUNCE,
    
    /**
     * 3 o más rebotes temporales en 30 días
     */
    SOFT_BOUNCE_REPETIDO,
    
    /**
     * Queja de spam reportada por el destinatario
     */
    COMPLAINT,
    
    /**
     * Bloqueado manualmente por un administrador
     */
    MANUAL
}
