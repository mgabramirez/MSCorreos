package com.acosux.MSCorreos.dtos;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * DTO para métricas agregadas de notificaciones.
 *
 * <p>Retornado por el endpoint GET /api/v1/notificaciones/estadisticas.
 * Agrupa conteos por empresa, tipo_notificacion y tipo de evento.</p>
 *
 * <p>Requirements: 7.6</p>
 *
 * @author MSCorreos Team
 */
public class EstadisticasNotificacionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Total de notificaciones registradas en el sistema. */
    private Long totalNotificaciones;

    /**
     * Conteo de notificaciones agrupado por tipo de evento
     * (Send, Delivery, Open, Bounce, Complaint, etc.).
     */
    private Map<String, Long> porTipoEvento;

    /**
     * Conteo de notificaciones agrupado por empresa.
     */
    private Map<String, Long> porEmpresa;

    /**
     * Conteo de notificaciones agrupado por tipo_notificacion.
     */
    private Map<String, Long> porTipoNotificacion;

    /**
     * Detalle de conteos por empresa y tipo_notificacion.
     * Cada entrada contiene empresa, tipoNotificacion y total.
     */
    private List<ConteoEmpresaTipoDTO> detalleEmpresaTipo;

    public EstadisticasNotificacionDTO() {
    }

    // -------------------------------------------------------------------------
    // Getters y Setters
    // -------------------------------------------------------------------------

    public Long getTotalNotificaciones() {
        return totalNotificaciones;
    }

    public void setTotalNotificaciones(Long totalNotificaciones) {
        this.totalNotificaciones = totalNotificaciones;
    }

    public Map<String, Long> getPorTipoEvento() {
        return porTipoEvento;
    }

    public void setPorTipoEvento(Map<String, Long> porTipoEvento) {
        this.porTipoEvento = porTipoEvento;
    }

    public Map<String, Long> getPorEmpresa() {
        return porEmpresa;
    }

    public void setPorEmpresa(Map<String, Long> porEmpresa) {
        this.porEmpresa = porEmpresa;
    }

    public Map<String, Long> getPorTipoNotificacion() {
        return porTipoNotificacion;
    }

    public void setPorTipoNotificacion(Map<String, Long> porTipoNotificacion) {
        this.porTipoNotificacion = porTipoNotificacion;
    }

    public List<ConteoEmpresaTipoDTO> getDetalleEmpresaTipo() {
        return detalleEmpresaTipo;
    }

    public void setDetalleEmpresaTipo(List<ConteoEmpresaTipoDTO> detalleEmpresaTipo) {
        this.detalleEmpresaTipo = detalleEmpresaTipo;
    }

    // -------------------------------------------------------------------------
    // Inner DTO
    // -------------------------------------------------------------------------

    /**
     * Conteo de notificaciones por empresa y tipo_notificacion.
     */
    public static class ConteoEmpresaTipoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String empresa;
        private String tipoNotificacion;
        private Long total;

        public ConteoEmpresaTipoDTO() {
        }

        public ConteoEmpresaTipoDTO(String empresa, String tipoNotificacion, Long total) {
            this.empresa = empresa;
            this.tipoNotificacion = tipoNotificacion;
            this.total = total;
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

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }
    }
}
