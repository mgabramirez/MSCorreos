package com.acosux.MSCorreos.entidades;

import java.io.Serializable;
import java.util.Date;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Configuración de AWS SES por empresa para el microservicio MSCorreos.
 * Mapea la tabla correos.cor_configuracion_empresa.
 */
@Entity
@Table(name = "cor_configuracion_empresa", schema = "correos")
public class CorreosConfiguracionEmpresa implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Basic(optional = false)
    @Column(name = "id")
    private Integer id;

    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "emp_codigo", unique = true)
    private String empCodigo;

    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "correo_emisor")
    private String correoEmisor;

    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "nombre_emisor")
    private String nombreEmisor;

    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "configuration_set")
    private String configurationSet;

    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "region_aws")
    private String regionAws;

    @Column(name = "url_logo")
    private String urlLogo;

    @Column(name = "html_header")
    private String htmlHeader;

    @Column(name = "html_footer")
    private String htmlFooter;

    @Basic(optional = false)
    @NotNull
    @Column(name = "es_defecto")
    private Boolean esDefecto;

    @Basic(optional = false)
    @NotNull
    @Size(min = 1, max = 2147483647)
    @Column(name = "usr_codigo")
    private String usrCodigo;

    @Basic(optional = false)
    @NotNull
    @Column(name = "usr_fecha")
    @Temporal(TemporalType.TIMESTAMP)
    private Date usrFecha;

    public CorreosConfiguracionEmpresa() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEmpCodigo() {
        return empCodigo;
    }

    public void setEmpCodigo(String empCodigo) {
        this.empCodigo = empCodigo;
    }

    public String getCorreoEmisor() {
        return correoEmisor;
    }

    public void setCorreoEmisor(String correoEmisor) {
        this.correoEmisor = correoEmisor;
    }

    public String getNombreEmisor() {
        return nombreEmisor;
    }

    public void setNombreEmisor(String nombreEmisor) {
        this.nombreEmisor = nombreEmisor;
    }

    public String getConfigurationSet() {
        return configurationSet;
    }

    public void setConfigurationSet(String configurationSet) {
        this.configurationSet = configurationSet;
    }

    public String getRegionAws() {
        return regionAws;
    }

    public void setRegionAws(String regionAws) {
        this.regionAws = regionAws;
    }

    public String getUrlLogo() {
        return urlLogo;
    }

    public void setUrlLogo(String urlLogo) {
        this.urlLogo = urlLogo;
    }

    public String getHtmlHeader() {
        return htmlHeader;
    }

    public void setHtmlHeader(String htmlHeader) {
        this.htmlHeader = htmlHeader;
    }

    public String getHtmlFooter() {
        return htmlFooter;
    }

    public void setHtmlFooter(String htmlFooter) {
        this.htmlFooter = htmlFooter;
    }

    public Boolean getEsDefecto() {
        return esDefecto;
    }

    public void setEsDefecto(Boolean esDefecto) {
        this.esDefecto = esDefecto;
    }

    public String getUsrCodigo() {
        return usrCodigo;
    }

    public void setUsrCodigo(String usrCodigo) {
        this.usrCodigo = usrCodigo;
    }

    public Date getUsrFecha() {
        return usrFecha;
    }

    public void setUsrFecha(Date usrFecha) {
        this.usrFecha = usrFecha;
    }

    @Override
    public String toString() {
        return "CorreosConfiguracionEmpresa[ empCodigo=" + empCodigo + " ]";
    }
}
