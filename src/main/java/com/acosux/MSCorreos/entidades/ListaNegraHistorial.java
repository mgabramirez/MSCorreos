/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.acosux.MSCorreos.entidades;

import java.io.Serializable;
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
 * Entidad que representa el historial de cambios en la lista negra
 * para auditoría y trazabilidad
 * 
 * @author MSCorreos Team
 */
@Entity
@Table(name = "cor_lista_negra_historial", schema = "correos")
public class ListaNegraHistorial implements Serializable {

    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Long id;
    
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 255)
    @Column(name = "email")
    private String email;
    
    @Basic(optional = false)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "accion", length = 20)
    private AccionListaNegra accion;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;
    
    @Size(max = 100)
    @Column(name = "usuario")
    private String usuario;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "fecha")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fecha;

    public ListaNegraHistorial() {
        this.fecha = new Date();
    }

    public ListaNegraHistorial(String email, AccionListaNegra accion, String motivo, String usuario) {
        this.email = email;
        this.accion = accion;
        this.motivo = motivo;
        this.usuario = usuario;
        this.fecha = new Date();
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

    public AccionListaNegra getAccion() {
        return accion;
    }

    public void setAccion(AccionListaNegra accion) {
        this.accion = accion;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public Date getFecha() {
        return fecha;
    }

    public void setFecha(Date fecha) {
        this.fecha = fecha;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof ListaNegraHistorial)) {
            return false;
        }
        ListaNegraHistorial other = (ListaNegraHistorial) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ListaNegraHistorial{" + "id=" + id + ", email=" + email + ", accion=" + accion + ", fecha=" + fecha + '}';
    }
}
