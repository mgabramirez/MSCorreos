package com.acosux.MSCorreos.valueobjects;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object que representa una dirección de correo electrónico válida.
 * Implementa validación según RFC 5322 (simplificada).
 * 
 * Este objeto es inmutable y garantiza que solo contenga direcciones de email válidas.
 * 
 * @author MSCorreos Team
 * @version 1.0
 */
public class EmailAddress implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Patrón de validación de email basado en RFC 5322 (simplificado).
     * Acepta formatos comunes de email: usuario@dominio.tld
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private final String value;
    
    /**
     * Constructor que crea un EmailAddress validando el formato.
     * 
     * @param email La dirección de correo electrónico a validar
     * @throws IllegalArgumentException si el email es nulo, vacío o no cumple con RFC 5322
     */
    public EmailAddress(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email no puede ser nulo o vacío");
        }
        
        String normalizedEmail = email.toLowerCase().trim();
        
        if (!isValid(normalizedEmail)) {
            throw new IllegalArgumentException("Email inválido: " + email + ". Debe cumplir con RFC 5322");
        }
        
        this.value = normalizedEmail;
    }
    
    /**
     * Valida si una cadena cumple con el formato de email RFC 5322.
     * 
     * @param email La cadena a validar
     * @return true si el email es válido, false en caso contrario
     */
    private boolean isValid(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        
        // Validar longitud máxima (RFC 5321: 254 caracteres)
        if (email.length() > 254) {
            return false;
        }
        
        // Validar con regex
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return false;
        }
        
        // Validar que tenga exactamente un @
        long atCount = email.chars().filter(ch -> ch == '@').count();
        if (atCount != 1) {
            return false;
        }
        
        // Validar longitud de partes local y dominio
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return false;
        }
        
        String localPart = parts[0];
        String domainPart = parts[1];
        
        // RFC 5321: parte local máximo 64 caracteres
        if (localPart.length() > 64) {
            return false;
        }
        
        // RFC 5321: dominio máximo 255 caracteres
        if (domainPart.length() > 255) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Obtiene el valor normalizado del email (lowercase, trimmed).
     * 
     * @return La dirección de email normalizada
     */
    public String getValue() {
        return value;
    }
    
    /**
     * Obtiene la parte local del email (antes del @).
     * 
     * @return La parte local del email
     */
    public String getLocalPart() {
        return value.split("@")[0];
    }
    
    /**
     * Obtiene el dominio del email (después del @).
     * 
     * @return El dominio del email
     */
    public String getDomain() {
        return value.split("@")[1];
    }
    
    /**
     * Verifica si el email pertenece a un dominio específico.
     * 
     * @param domain El dominio a verificar (ej: "gmail.com")
     * @return true si el email pertenece al dominio especificado
     */
    public boolean belongsToDomain(String domain) {
        if (domain == null) {
            return false;
        }
        return getDomain().equalsIgnoreCase(domain.trim());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailAddress that = (EmailAddress) o;
        return Objects.equals(value, that.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return value;
    }
    
    /**
     * Método estático para validar un email sin crear una instancia.
     * 
     * @param email El email a validar
     * @return true si el email es válido según RFC 5322
     */
    public static boolean isValidEmail(String email) {
        try {
            new EmailAddress(email);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
