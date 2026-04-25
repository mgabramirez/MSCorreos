package com.acosux.MSCorreos.application.usecases;

import com.acosux.MSCorreos.dtos.EstadisticasNotificacionDTO;
import com.acosux.MSCorreos.dtos.FiltrosNotificacion;
import com.acosux.MSCorreos.dtos.NotificacionDTO;
import com.acosux.MSCorreos.dtos.NotificacionDetalleDTO;
import com.acosux.MSCorreos.entidades.CorreosNotificaciones;
import com.acosux.MSCorreos.infrastructure.exceptions.NotificacionNotFoundException;
import com.acosux.MSCorreos.repositories.NotificacionesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementación del caso de uso para consultar notificaciones.
 * 
 * Esta implementación:
 * - Aplica filtros dinámicos usando Spring Data JPA Specifications
 * - Implementa paginación
 * - Mapea entidades a DTOs
 * - Maneja el detalle completo incluyendo JSON del evento
 * 
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 * 
 * @author MSCorreos Team
 */
@Service
@Transactional(readOnly = true)
public class ConsultarNotificacionesUseCaseImpl implements ConsultarNotificacionesUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(ConsultarNotificacionesUseCaseImpl.class);
    
    private final NotificacionesRepository notificacionesRepository;
    
    @Autowired
    public ConsultarNotificacionesUseCaseImpl(NotificacionesRepository notificacionesRepository) {
        this.notificacionesRepository = notificacionesRepository;
    }
    
    /**
     * Ejecuta la consulta de notificaciones con filtros y paginación.
     * 
     * Aplica filtros dinámicos basados en los criterios proporcionados:
     * - empresa: filtro exacto
     * - ruc: filtro exacto
     * - tipoNotificacion: filtro exacto
     * - fechaInicio/fechaFin: rango de fechas
     * - destinatario: búsqueda parcial (LIKE)
     * - tipo: filtro exacto
     * 
     * @param filtros Filtros de búsqueda
     * @param pageable Configuración de paginación
     * @return Página de NotificacionDTO
     */
    @Override
    public Page<NotificacionDTO> ejecutar(FiltrosNotificacion filtros, Pageable pageable) {
        log.debug("Consultando notificaciones con filtros: empresa={}, ruc={}, tipoNotificacion={}, " +
                  "fechaInicio={}, fechaFin={}, destinatario={}, tipo={}", 
                  filtros.getEmpresa(), filtros.getRuc(), filtros.getTipoNotificacion(),
                  filtros.getFechaInicio(), filtros.getFechaFin(), filtros.getDestinatario(), filtros.getTipo());
        
        try {
            // Construir especificación dinámica basada en filtros
            Specification<CorreosNotificaciones> spec = buildSpecification(filtros);
            
            // Ejecutar consulta con paginación
            Page<CorreosNotificaciones> entidades = notificacionesRepository.findAll(spec, pageable);
            
            // Mapear entidades a DTOs
            List<NotificacionDTO> dtos = entidades.getContent().stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
            
            log.debug("Consulta exitosa: {} notificaciones encontradas de {} totales", 
                      dtos.size(), entidades.getTotalElements());
            
            return new PageImpl<>(dtos, pageable, entidades.getTotalElements());
            
        } catch (Exception e) {
            log.error("Error consultando notificaciones", e);
            throw new RuntimeException("Error consultando notificaciones: " + e.getMessage(), e);
        }
    }
    
    /**
     * Obtiene el detalle completo de una notificación incluyendo el JSON del evento.
     * 
     * @param id ID de la notificación (n_secuencial)
     * @return NotificacionDetalleDTO con todos los campos incluyendo n_informe
     * @throws NotificacionNotFoundException si la notificación no existe
     */
    @Override
    public NotificacionDetalleDTO obtenerDetalle(Integer id) throws NotificacionNotFoundException {
        log.debug("Obteniendo detalle de notificación con id={}", id);
        
        if (id == null) {
            throw new IllegalArgumentException("El ID de la notificación no puede ser nulo");
        }
        
        Optional<CorreosNotificaciones> entidadOpt = notificacionesRepository.findById(id);
        
        if (!entidadOpt.isPresent()) {
            log.warn("Notificación no encontrada con id={}", id);
            throw new NotificacionNotFoundException("Notificación no encontrada con ID: " + id);
        }
        
        CorreosNotificaciones entidad = entidadOpt.get();
        NotificacionDetalleDTO detalle = mapToDetalleDTO(entidad);
        
        log.debug("Detalle de notificación obtenido exitosamente: id={}, destinatario={}", 
                  id, entidad.getnDestinatario());
        
        return detalle;
    }
    
    /**
     * Obtiene métricas agregadas de notificaciones agrupadas por tipo de evento,
     * empresa y tipo_notificacion.
     *
     * <p>Requirement: 7.6</p>
     *
     * @return DTO con métricas agregadas
     */
    @Override
    public EstadisticasNotificacionDTO obtenerEstadisticas() {
        log.debug("Calculando estadísticas de notificaciones");

        try {
            EstadisticasNotificacionDTO estadisticas = new EstadisticasNotificacionDTO();

            // Total de notificaciones
            estadisticas.setTotalNotificaciones(notificacionesRepository.count());

            // Agrupado por tipo de evento
            List<Object[]> porTipoRaw = notificacionesRepository.countGroupByTipo();
            Map<String, Long> porTipo = new LinkedHashMap<>();
            for (Object[] row : porTipoRaw) {
                porTipo.put((String) row[0], (Long) row[1]);
            }
            estadisticas.setPorTipoEvento(porTipo);

            // Agrupado por empresa
            List<Object[]> porEmpresaRaw = notificacionesRepository.countGroupByEmpresa();
            Map<String, Long> porEmpresa = new LinkedHashMap<>();
            for (Object[] row : porEmpresaRaw) {
                porEmpresa.put((String) row[0], (Long) row[1]);
            }
            estadisticas.setPorEmpresa(porEmpresa);

            // Agrupado por tipo_notificacion
            List<Object[]> porTipoNotifRaw = notificacionesRepository.countGroupByTipoNotificacion();
            Map<String, Long> porTipoNotif = new LinkedHashMap<>();
            for (Object[] row : porTipoNotifRaw) {
                porTipoNotif.put((String) row[0], (Long) row[1]);
            }
            estadisticas.setPorTipoNotificacion(porTipoNotif);

            // Detalle por empresa + tipo_notificacion
            List<Object[]> detalleRaw = notificacionesRepository.countGroupByEmpresaAndTipoNotificacion();
            List<EstadisticasNotificacionDTO.ConteoEmpresaTipoDTO> detalle = detalleRaw.stream()
                    .map(row -> new EstadisticasNotificacionDTO.ConteoEmpresaTipoDTO(
                            (String) row[0],
                            (String) row[1],
                            (Long) row[2]))
                    .collect(Collectors.toList());
            estadisticas.setDetalleEmpresaTipo(detalle);

            log.debug("Estadísticas calculadas: total={}", estadisticas.getTotalNotificaciones());
            return estadisticas;

        } catch (Exception e) {
            log.error("Error calculando estadísticas de notificaciones", e);
            throw new RuntimeException("Error calculando estadísticas: " + e.getMessage(), e);
        }
    }

    /**
     * Construye una especificación dinámica basada en los filtros proporcionados.
     * 
     * Utiliza Spring Data JPA Specifications para construir consultas dinámicas
     * que solo incluyen los filtros que no son nulos.
     * 
     * @param filtros Filtros de búsqueda
     * @return Specification para la consulta
     */
    private Specification<CorreosNotificaciones> buildSpecification(FiltrosNotificacion filtros) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // Filtro por empresa (exacto)
            if (filtros.getEmpresa() != null && !filtros.getEmpresa().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("nEmpresa"), filtros.getEmpresa()));
            }
            
            // Filtro por RUC (exacto)
            if (filtros.getRuc() != null && !filtros.getRuc().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("nRuc"), filtros.getRuc()));
            }
            
            // Filtro por tipo de notificación (exacto)
            if (filtros.getTipoNotificacion() != null && !filtros.getTipoNotificacion().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("nTipoNotificacion"), filtros.getTipoNotificacion()));
            }
            
            // Filtro por rango de fechas
            if (filtros.getFechaInicio() != null && filtros.getFechaFin() != null) {
                predicates.add(criteriaBuilder.between(root.get("nFecha"), 
                                                       filtros.getFechaInicio(), 
                                                       filtros.getFechaFin()));
            } else if (filtros.getFechaInicio() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("nFecha"), 
                                                                     filtros.getFechaInicio()));
            } else if (filtros.getFechaFin() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("nFecha"), 
                                                                  filtros.getFechaFin()));
            }
            
            // Filtro por destinatario (búsqueda parcial con LIKE)
            if (filtros.getDestinatario() != null && !filtros.getDestinatario().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("nDestinatario")), 
                    "%" + filtros.getDestinatario().toLowerCase() + "%"
                ));
            }
            
            // Filtro por tipo de evento (exacto)
            if (filtros.getTipo() != null && !filtros.getTipo().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("nTipo"), filtros.getTipo()));
            }
            
            // Combinar todos los predicados con AND
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Mapea una entidad CorreosNotificaciones a NotificacionDTO.
     * 
     * @param entidad Entidad a mapear
     * @return DTO con campos básicos (sin n_informe)
     */
    private NotificacionDTO mapToDTO(CorreosNotificaciones entidad) {
        NotificacionDTO dto = new NotificacionDTO();
        dto.setId(entidad.getnSecuencial());
        dto.setDestinatario(entidad.getnDestinatario());
        dto.setFecha(entidad.getnFecha());
        dto.setTipo(entidad.getnTipo());
        dto.setObservacion(entidad.getnObservacion());
        dto.setEmpresa(entidad.getnEmpresa());
        dto.setRuc(entidad.getnRuc());
        dto.setClave(entidad.getnClave());
        dto.setTipoNotificacion(entidad.getnTipoNotificacion());
        return dto;
    }
    
    /**
     * Mapea una entidad CorreosNotificaciones a NotificacionDetalleDTO.
     * 
     * Incluye todos los campos del DTO básico más el JSON completo del evento (n_informe).
     * 
     * @param entidad Entidad a mapear
     * @return DTO con todos los campos incluyendo n_informe
     */
    private NotificacionDetalleDTO mapToDetalleDTO(CorreosNotificaciones entidad) {
        NotificacionDetalleDTO detalle = new NotificacionDetalleDTO();
        detalle.setId(entidad.getnSecuencial());
        detalle.setDestinatario(entidad.getnDestinatario());
        detalle.setFecha(entidad.getnFecha());
        detalle.setTipo(entidad.getnTipo());
        detalle.setObservacion(entidad.getnObservacion());
        detalle.setEmpresa(entidad.getnEmpresa());
        detalle.setRuc(entidad.getnRuc());
        detalle.setClave(entidad.getnClave());
        detalle.setTipoNotificacion(entidad.getnTipoNotificacion());
        detalle.setInformeJson(entidad.getnInforme()); // JSON completo del evento
        return detalle;
    }
}
