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
 *
 * @author Mario
 */
@Entity
@Table(name = "cor_notificaciones", schema = "correos")
public class CorreosNotificaciones implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "n_secuencial")
    private Integer nSecuencial;
    
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "n_destinatario")
    private String nDestinatario;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "n_fecha")
    @Temporal(TemporalType.TIMESTAMP)
    private Date nFecha;
    
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "n_tipo")
    private String nTipo;
    
    @Size(min = 1, max = 2147483647)
    @Column(name = "n_observacion")
    private String nObservacion;
    
    @Basic(optional = false)
    @NotNull
    @Column(name = "n_informe")
    private String nInforme;
    /*+**************************************************/
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "n_empresa")
    private String nEmpresa;
    
    @Size(min = 1, max = 2147483647)
    @Column(name = "n_ruc")
    private String nRuc;
    
    @Size(min = 1, max = 2147483647)
    @Column(name = "n_clave")
    private String nClave;
    
    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "n_tipo_notificacion")
    private String nTipoNotificacion;

    public CorreosNotificaciones() {
    }

    public Integer getnSecuencial() {
        return nSecuencial;
    }

    public void setnSecuencial(Integer nSecuencial) {
        this.nSecuencial = nSecuencial;
    }

    public String getnDestinatario() {
        return nDestinatario;
    }

    public void setnDestinatario(String nDestinatario) {
        this.nDestinatario = nDestinatario;
    }

    public Date getnFecha() {
        return nFecha;
    }

    public void setnFecha(Date nFecha) {
        this.nFecha = nFecha;
    }

    public String getnTipo() {
        return nTipo;
    }

    public void setnTipo(String nTipo) {
        this.nTipo = nTipo;
    }

    public String getnObservacion() {
        return nObservacion;
    }

    public void setnObservacion(String nObservacion) {
        this.nObservacion = nObservacion;
    }

    public String getnInforme() {
        return nInforme;
    }

    public void setnInforme(String nInforme) {
        this.nInforme = nInforme;
    }

    public String getnEmpresa() {
        return nEmpresa;
    }

    public void setnEmpresa(String nEmpresa) {
        this.nEmpresa = nEmpresa;
    }

    public String getnRuc() {
        return nRuc;
    }

    public void setnRuc(String nRuc) {
        this.nRuc = nRuc;
    }

    public String getnClave() {
        return nClave;
    }

    public void setnClave(String nClave) {
        this.nClave = nClave;
    }

    public String getnTipoNotificacion() {
        return nTipoNotificacion;
    }

    public void setnTipoNotificacion(String nTipoNotificacion) {
        this.nTipoNotificacion = nTipoNotificacion;
    }

    
    
}
