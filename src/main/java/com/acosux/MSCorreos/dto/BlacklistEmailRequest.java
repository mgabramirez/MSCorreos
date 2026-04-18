package com.acosux.MSCorreos.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * DTO de solicitud para agregar email a blacklist.
 */
public class BlacklistEmailRequest {
    
    @NotBlank(message = "El email no puede estar vacío")
    @Email(message = "El formato del email no es válido")
    private String email;
    
    @Size(max = 500, message = "La razón no puede exceder 500 caracteres")
    private String razon;
    
    public BlacklistEmailRequest() {}
    
    public BlacklistEmailRequest(String email, String razon) {
        this.email = email;
        this.razon = razon;
    }
    
    // Getters and Setters
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getRazon() {
        return razon;
    }
    
    public void setRazon(String razon) {
        this.razon = razon;
    }
}
