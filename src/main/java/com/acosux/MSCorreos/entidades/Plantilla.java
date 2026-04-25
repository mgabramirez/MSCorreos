/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.acosux.MSCorreos.entidades;

import java.io.Serializable;
import java.util.Date;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entidad que representa plantillas de correo electrónico
 * (Funcionalidad futura)
 * 
 * @author MSCorreos Team
 */
@Entity
@Table(name = "cor_plantillas", schema = "correos")
public class Plantilla implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Long id;
    
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 100)
    @Column(name = "empresa")
    private String empresa;
    
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 100)
    @Column(name = "tipo_notificacion")
    private String tipoNotificacion;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "asunto_template", columnDefinition = "TEXT")
    private String asuntoTemplate;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "cuerpo_html_template", columnDefinition = "TEXT")
    private String cuerpoHtmlTemplate;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "cuerpo_texto_template", columnDefinition = "TEXT")
    private String cuerpoTextoTemplate;
    
    @Column(name = "fecha_creacion")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaCreacion;
    
    @Column(name = "fecha_modificacion")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fechaModificacion;

    public Plantilla() {
        this.fechaCreacion = new Date();
    }

    // Getters y Setters
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmpresa() {
        return empresa;
    }

    public void setEmpresa(String empresa) {
        this.empresa = empresa;
    }

    public String getTipoNotificacion() {
        return tipoNotificacion;
    }

    public void setTipoNotificacion(String tipoNotificacion) {
        this.tipoNotificacion = tipoNotificacion;
    }

    public String getAsuntoTemplate() {
        return asuntoTemplate;
    }

    public void setAsuntoTemplate(String asuntoTemplate) {
        this.asuntoTemplate = asuntoTemplate;
    }

    public String getCuerpoHtmlTemplate() {
        return cuerpoHtmlTemplate;
    }

    public void setCuerpoHtmlTemplate(String cuerpoHtmlTemplate) {
        this.cuerpoHtmlTemplate = cuerpoHtmlTemplate;
    }

    public String getCuerpoTextoTemplate() {
        return cuerpoTextoTemplate;
    }

    public void setCuerpoTextoTemplate(String cuerpoTextoTemplate) {
        this.cuerpoTextoTemplate = cuerpoTextoTemplate;
    }

    public Date getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(Date fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public Date getFechaModificacion() {
        return fechaModificacion;
    }

    public void setFechaModificacion(Date fechaModificacion) {
        this.fechaModificacion = fechaModificacion;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Plantilla)) {
            return false;
        }
        Plantilla other = (Plantilla) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Plantilla{" + "id=" + id + ", empresa=" + empresa + ", tipoNotificacion=" + tipoNotificacion + '}';
    }
}
