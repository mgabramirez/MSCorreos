package com.acosux.MSCorreos.entidades;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * Entidad para la tabla global de blacklist de correos electrónicos.
 * Esta tabla NO está asociada a ninguna empresa (es global).
 */
@Entity
@Table(name = "blacklist_emails")
public class BlacklistEmail {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 255)
    private String email;
    
    @Column(length = 500)
    private String razon;
    
    @Column(name = "fecha_bloqueo", nullable = false)
    private LocalDateTime fechaBloqueo;
    
    @Column(name = "bloqueado_por", length = 100)
    private String bloqueadoPor;
    
    @Column(nullable = false)
    private Boolean activo = true;
    
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;
    
    @Column(name = "usuario_creacion", length = 100)
    private String usuarioCreacion;
    
    @Column(name = "usuario_actualizacion", length = 100)
    private String usuarioActualizacion;
    
    // Getters and Setters
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
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
    
    public LocalDateTime getFechaBloqueo() {
        return fechaBloqueo;
    }
    
    public void setFechaBloqueo(LocalDateTime fechaBloqueo) {
        this.fechaBloqueo = fechaBloqueo;
    }
    
    public String getBloqueadoPor() {
        return bloqueadoPor;
    }
    
    public void setBloqueadoPor(String bloqueadoPor) {
        this.bloqueadoPor = bloqueadoPor;
    }
    
    public Boolean getActivo() {
        return activo;
    }
    
    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
    
    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
    
    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
    
    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
    
    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
    
    public String getUsuarioCreacion() {
        return usuarioCreacion;
    }
    
    public void setUsuarioCreacion(String usuarioCreacion) {
        this.usuarioCreacion = usuarioCreacion;
    }
    
    public String getUsuarioActualizacion() {
        return usuarioActualizacion;
    }
    
    public void setUsuarioActualizacion(String usuarioActualizacion) {
        this.usuarioActualizacion = usuarioActualizacion;
    }
    
    @PrePersist
    protected void onCreate() {
        fechaCreacion = LocalDateTime.now();
        fechaBloqueo = LocalDateTime.now();
    }
}