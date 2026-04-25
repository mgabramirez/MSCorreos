package com.acosux.MSCorreos.application.usecases;

import com.acosux.MSCorreos.dtos.FiltrosListaNegra;
import com.acosux.MSCorreos.dtos.ListaNegraDTO;
import com.acosux.MSCorreos.entidades.ListaNegra;
import com.acosux.MSCorreos.entidades.ListaNegraHistorial;
import com.acosux.MSCorreos.enums.TipoBloqueo;
import com.acosux.MSCorreos.repositories.ListaNegraRepository;
import com.acosux.MSCorreos.service.BlacklistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementación del caso de uso para gestionar la lista negra de correos bloqueados.
 *
 * <p>Delega todas las operaciones de persistencia y cache en {@link BlacklistService},
 * que es el servicio de infraestructura centralizado para la lista negra.
 * Este caso de uso se encarga de la lógica de aplicación: validación de filtros,
 * conversión de DTOs y coordinación de operaciones.</p>
 *
 * <p>Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6</p>
 *
 * @author MSCorreos Team
 */
@Service
@Transactional
public class GestionarListaNegraUseCaseImpl implements GestionarListaNegraUseCase {

    private static final Logger log = LoggerFactory.getLogger(GestionarListaNegraUseCaseImpl.class);

    private final BlacklistService blacklistService;
    private final ListaNegraRepository listaNegraRepository;

    @Autowired
    public GestionarListaNegraUseCaseImpl(
            BlacklistService blacklistService,
            ListaNegraRepository listaNegraRepository) {
        this.blacklistService = blacklistService;
        this.listaNegraRepository = listaNegraRepository;
    }

    /**
     * Agrega un email a la lista negra con registro en historial.
     *
     * <p>Delega en {@link BlacklistService#agregarAListaNegra} que maneja
     * upsert, historial e invalidación de cache.</p>
     *
     * @param email  Email a bloquear
     * @param motivo Motivo del bloqueo
     * @param tipo   Tipo de bloqueo
     * @throws IllegalArgumentException si el email es inválido
     */
    @Override
    public void agregar(String email, String motivo, TipoBloqueo tipo) {
        log.info("Caso de uso: agregar email a lista negra: email={}, tipo={}", email, tipo);
        // Convertir TipoBloqueo del paquete enums al paquete entidades
        com.acosux.MSCorreos.entidades.TipoBloqueo tipoEntidad = convertirTipoBloqueo(tipo);
        blacklistService.agregarAListaNegra(email, motivo, tipoEntidad, null);
    }

    /**
     * Remueve un email de la lista negra (soft-delete) con registro en historial.
     *
     * <p>Delega en {@link BlacklistService#removerDeListaNegra}.</p>
     *
     * @param email   Email a desbloquear
     * @param motivo  Motivo de la remoción
     * @param usuario Usuario que realiza la acción
     * @throws IllegalArgumentException si el email no existe en la lista negra
     */
    @Override
    public void remover(String email, String motivo, String usuario) {
        log.info("Caso de uso: remover email de lista negra: email={}, usuario={}", email, usuario);
        blacklistService.removerDeListaNegra(email, motivo, usuario);
    }

    /**
     * Verifica si un email está en lista negra (con cache Caffeine TTL 5 min).
     *
     * <p>Delega en {@link BlacklistService#estaEnListaNegra} que gestiona el cache.</p>
     *
     * @param email Email a verificar
     * @return {@code true} si está bloqueado y activo
     */
    @Override
    @Transactional(readOnly = true)
    public boolean estaEnListaNegra(String email) {
        return blacklistService.estaEnListaNegra(email);
    }

    /**
     * Consulta lista negra con filtros y paginación.
     *
     * <p>Aplica filtros usando métodos del repositorio:</p>
     * <ul>
     *   <li>{@code tipoBloqueo}: filtra por tipo de bloqueo</li>
     *   <li>{@code fechaDesde}/{@code fechaHasta}: filtra por rango de fecha_registro</li>
     *   <li>{@code activo}: filtra por estado activo/inactivo</li>
     * </ul>
     *
     * @param filtros  Filtros de búsqueda
     * @param pageable Configuración de paginación
     * @return Página de emails bloqueados que cumplen con los filtros
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ListaNegraDTO> consultar(FiltrosListaNegra filtros, Pageable pageable) {
        log.debug("Consultando lista negra: tipoBloqueo={}, activo={}, fechaDesde={}, fechaHasta={}",
                filtros.getTipoBloqueo(), filtros.getActivo(),
                filtros.getFechaDesde(), filtros.getFechaHasta());

        try {
            Page<ListaNegra> resultado;

            if (filtros.getTipoBloqueo() != null && !filtros.getTipoBloqueo().isEmpty()) {
                try {
                    com.acosux.MSCorreos.entidades.TipoBloqueo tipo =
                            com.acosux.MSCorreos.entidades.TipoBloqueo.valueOf(filtros.getTipoBloqueo());
                    resultado = listaNegraRepository.findByTipoBloqueo(tipo, pageable);
                } catch (IllegalArgumentException e) {
                    log.warn("Tipo de bloqueo inválido en filtros: {}", filtros.getTipoBloqueo());
                    return new PageImpl<>(new ArrayList<>(), pageable, 0);
                }
            } else if (filtros.getFechaDesde() != null && filtros.getFechaHasta() != null) {
                resultado = listaNegraRepository.findByFechaRegistroBetween(
                        filtros.getFechaDesde(), filtros.getFechaHasta(), pageable);
            } else if (filtros.getActivo() != null && filtros.getActivo()) {
                resultado = listaNegraRepository.findByActivoTrue(pageable);
            } else {
                resultado = listaNegraRepository.findAll(pageable);
            }

            // Aplicar filtro adicional de activo si está presente y no se usó como criterio principal
            List<ListaNegra> contenido = resultado.getContent();
            if (filtros.getActivo() != null
                    && (filtros.getTipoBloqueo() != null || filtros.getFechaDesde() != null)) {
                contenido = contenido.stream()
                        .filter(ln -> ln.getActivo().equals(filtros.getActivo()))
                        .collect(Collectors.toList());
            }

            List<ListaNegraDTO> dtos = contenido.stream()
                    .map(this::convertirADTO)
                    .collect(Collectors.toList());

            log.debug("Consulta completada: total={}", resultado.getTotalElements());
            return new PageImpl<>(dtos, pageable, resultado.getTotalElements());

        } catch (Exception e) {
            log.error("Error consultando lista negra con filtros", e);
            throw new RuntimeException("Error consultando lista negra: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene historial de cambios de un email específico.
     *
     * <p>Delega en {@link BlacklistService#obtenerHistorial}.</p>
     *
     * @param email Email a consultar
     * @return Lista de cambios históricos ordenados por fecha descendente
     */
    @Override
    @Transactional(readOnly = true)
    public List<ListaNegraHistorial> obtenerHistorial(String email) {
        log.debug("Obteniendo historial para email: {}", email);
        return blacklistService.obtenerHistorial(email);
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    /**
     * Convierte {@link TipoBloqueo} del paquete {@code enums} al paquete {@code entidades}.
     */
    private com.acosux.MSCorreos.entidades.TipoBloqueo convertirTipoBloqueo(TipoBloqueo tipo) {
        switch (tipo) {
            case HARD_BOUNCE:        return com.acosux.MSCorreos.entidades.TipoBloqueo.HARD_BOUNCE;
            case SOFT_BOUNCE_REPETIDO: return com.acosux.MSCorreos.entidades.TipoBloqueo.SOFT_BOUNCE_REPETIDO;
            case COMPLAINT:          return com.acosux.MSCorreos.entidades.TipoBloqueo.COMPLAINT;
            case MANUAL:             return com.acosux.MSCorreos.entidades.TipoBloqueo.MANUAL;
            default: throw new IllegalArgumentException("Tipo de bloqueo no reconocido: " + tipo);
        }
    }

    /**
     * Convierte una entidad {@link ListaNegra} a {@link ListaNegraDTO}.
     */
    private ListaNegraDTO convertirADTO(ListaNegra entidad) {
        ListaNegraDTO dto = new ListaNegraDTO();
        dto.setId(entidad.getId());
        dto.setEmail(entidad.getEmail());
        dto.setMotivo(entidad.getMotivo());
        dto.setFechaRegistro(entidad.getFechaRegistro());
        dto.setTipoBloqueo(entidad.getTipoBloqueo() != null ? entidad.getTipoBloqueo().name() : null);
        dto.setActivo(entidad.getActivo());
        dto.setContadorSoftBounce(entidad.getContadorSoftBounce());
        dto.setUltimoSoftBounce(entidad.getUltimoSoftBounce());
        return dto;
    }
}
