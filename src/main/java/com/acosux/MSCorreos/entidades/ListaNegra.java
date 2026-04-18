/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.acosux.MSCorreos.entidades;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Entidad que representa la lista negra de correos bloqueados
 * 
 * @author MSCorreos Team
 */
@Entity
@Table(name = "cor_lista_negra", schema = "correos")
public class ListaNegra implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Long id;
    
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 255)
    @Column(name = "email", unique = true)
    private String email;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "fecha_registro")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaRegistro;
    
    @Basic(optional = false)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_bloqueo", length = 50)
    private TipoBloqueo tipoBloqueo;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "activo")
    private Boolean activo;
    
    @Column(name = "contador_soft_bounce")
    private Integer contadorSoftBounce;
    
    @Column(name = "ultimo_soft_bounce")
    @Temporal(TemporalType.TIMESTAMP)
    private Date ultimoSoftBounce;

    public ListaNegra() {
        this.activo = true;
        this.contadorSoftBounce = 0;
        this.fechaRegistro = new Date();
    }

    /**
     * Incrementa el contador de soft bounces y actualiza la fecha del último bounce.
     * Si alcanza 3 o más soft bounces, cambia el tipo de bloqueo a SOFT_BOUNCE_REPETIDO
     * y activa el bloqueo.
     */
    public void incrementarSoftBounce() {
        this.contadorSoftBounce = (this.contadorSoftBounce == null) ? 1 : this.contadorSoftBounce + 1;
        this.ultimoSoftBounce = new Date();
        
        if (this.contadorSoftBounce >= 3) {
            this.tipoBloqueo = TipoBloqueo.SOFT_BOUNCE_REPETIDO;
            this.activo = true;
        }
    }
    
    /**
     * Determina si el email debe ser bloqueado basándose en el estado actual.
     * Para SOFT_BOUNCE_REPETIDO, verifica que no hayan pasado más de 30 días
     * desde el último soft bounce.
     * 
     * @return true si el email debe ser bloqueado, false en caso contrario
     */
    public boolean debeSerBloqueado() {
        if (!activo) {
            return false;
        }
        
        if (tipoBloqueo == TipoBloqueo.SOFT_BOUNCE_REPETIDO && ultimoSoftBounce != null) {
            // Verificar si han pasado más de 30 días desde último soft bounce
            long diasDesdeUltimoBounce = ChronoUnit.DAYS.between(
                ultimoSoftBounce.toInstant(), 
                Instant.now()
            );
            return diasDesdeUltimoBounce <= 30;
        }
        
        return true;
    }

    // Getters y Setters
    
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

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Date getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(Date fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public TipoBloqueo getTipoBloqueo() {
        return tipoBloqueo;
    }

    public void setTipoBloqueo(TipoBloqueo tipoBloqueo) {
        this.tipoBloqueo = tipoBloqueo;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    public Integer getContadorSoftBounce() {
        return contadorSoftBounce;
    }

    public void setContadorSoftBounce(Integer contadorSoftBounce) {
        this.contadorSoftBounce = contadorSoftBounce;
    }

    public Date getUltimoSoftBounce() {
        return ultimoSoftBounce;
    }

    public void setUltimoSoftBounce(Date ultimoSoftBounce) {
        this.ultimoSoftBounce = ultimoSoftBounce;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ListaNegra)) {
            return false;
        }
        ListaNegra other = (ListaNegra) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ListaNegra{" + "id=" + id + ", email=" + email + ", tipoBloqueo=" + tipoBloqueo + ", activo=" + activo + '}';
    }
}
