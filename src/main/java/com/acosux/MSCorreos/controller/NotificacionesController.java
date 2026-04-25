package com.acosux.MSCorreos.controller;

import com.acosux.MSCorreos.application.usecases.ConsultarNotificacionesUseCase;
import com.acosux.MSCorreos.dtos.EstadisticasNotificacionDTO;
import com.acosux.MSCorreos.dtos.FiltrosNotificacion;
import com.acosux.MSCorreos.dtos.NotificacionDetalleDTO;
import com.acosux.MSCorreos.dtos.NotificacionDTO;
import com.acosux.MSCorreos.infrastructure.exceptions.NotificacionNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para consulta de notificaciones de correo.
 *
 * <p>Expone los siguientes endpoints:</p>
 * <ul>
 *   <li>{@code GET /api/v1/notificaciones} — Lista notificaciones con filtros y paginación</li>
 *   <li>{@code GET /api/v1/notificaciones/estadisticas} — Métricas agregadas</li>
 *   <li>{@code GET /api/v1/notificaciones/{id}} — Detalle completo de una notificación</li>
 * </ul>
 *
 * <p>Todos los endpoints requieren autenticación mediante API Key en el header
 * {@code X-API-Key}. Las solicitudes sin API Key válida reciben HTTP 401.</p>
 *
 * <p>Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 13.1, 13.5</p>
 *
 * @author MSCorreos Team
 */
@RestController
@RequestMapping("/api/v1/notificaciones")
public class NotificacionesController {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesController.class);

    /** Tamaño máximo de página permitido (Requirement 7.3). */
    private static final int MAX_PAGE_SIZE = 100;

    /** Tamaño de página por defecto. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ConsultarNotificacionesUseCase consultarNotificacionesUseCase;

    /**
     * API Key configurada en application.properties bajo la clave {@code mscorreos.api.key}.
     * Si no está configurada, se usa un valor vacío que rechazará todas las solicitudes.
     */
    @Value("${mscorreos.api.key:}")
    private String apiKey;

    @Autowired
    public NotificacionesController(ConsultarNotificacionesUseCase consultarNotificacionesUseCase) {
        this.consultarNotificacionesUseCase = consultarNotificacionesUseCase;
    }

    // =========================================================================
    // GET /api/v1/notificaciones
    // =========================================================================

    /**
     * Lista notificaciones con filtros opcionales y paginación.
     *
     * <p>Filtros disponibles (todos opcionales):</p>
     * <ul>
     *   <li>{@code empresa} — Filtro exacto por empresa</li>
     *   <li>{@code ruc} — Filtro exacto por RUC</li>
     *   <li>{@code tipoNotificacion} — Filtro exacto por tipo de notificación</li>
     *   <li>{@code fechaInicio} — Fecha de inicio del rango (formato ISO: yyyy-MM-dd)</li>
     *   <li>{@code fechaFin} — Fecha de fin del rango (formato ISO: yyyy-MM-dd)</li>
     *   <li>{@code destinatario} — Búsqueda parcial por email del destinatario</li>
     *   <li>{@code tipo} — Filtro exacto por tipo de evento (Send, Delivery, Bounce, etc.)</li>
     * </ul>
     *
     * <p>Parámetros de paginación:</p>
     * <ul>
     *   <li>{@code page} — Número de página (0-based, default 0)</li>
     *   <li>{@code size} — Tamaño de página (default 20, máximo 100)</li>
     * </ul>
     *
     * @param apiKeyHeader API Key en header X-API-Key (requerido)
     * @param empresa      Filtro por empresa
     * @param ruc          Filtro por RUC
     * @param tipoNotificacion Filtro por tipo de notificación
     * @param fechaInicio  Fecha de inicio del rango
     * @param fechaFin     Fecha de fin del rango
     * @param destinatario Búsqueda parcial por destinatario
     * @param tipo         Filtro por tipo de evento
     * @param page         Número de página (default 0)
     * @param size         Tamaño de página (default 20, máximo 100)
     * @return Página de notificaciones con metadatos de paginación, o HTTP 401 si API Key inválida
     */
    @GetMapping
    public ResponseEntity<?> listarNotificaciones(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @RequestParam(required = false) String empresa,
            @RequestParam(required = false) String ruc,
            @RequestParam(required = false) String tipoNotificacion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaFin,
            @RequestParam(required = false) String destinatario,
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Requirement 13.1, 13.5: Validar API Key
        if (!esApiKeyValida(apiKeyHeader)) {
            log.warn("Acceso denegado a GET /api/v1/notificaciones: API Key inválida o ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("API Key inválida o ausente. Incluya el header X-API-Key."));
        }

        // Requirement 7.3: Limitar tamaño de página a MAX_PAGE_SIZE
        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        }

        log.info("GET /api/v1/notificaciones: empresa={}, ruc={}, tipoNotificacion={}, " +
                 "fechaInicio={}, fechaFin={}, destinatario={}, tipo={}, page={}, size={}",
                empresa, ruc, tipoNotificacion, fechaInicio, fechaFin, destinatario, tipo, page, pageSize);

        // Construir filtros
        FiltrosNotificacion filtros = FiltrosNotificacion.builder()
                .empresa(empresa)
                .ruc(ruc)
                .tipoNotificacion(tipoNotificacion)
                .fechaInicio(fechaInicio)
                .fechaFin(fechaFin)
                .destinatario(destinatario)
                .tipo(tipo)
                .build();

        // Paginación ordenada por fecha descendente
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "nFecha"));

        try {
            Page<NotificacionDTO> resultado = consultarNotificacionesUseCase.ejecutar(filtros, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("contenido", resultado.getContent());
            response.put("paginaActual", resultado.getNumber());
            response.put("totalPaginas", resultado.getTotalPages());
            response.put("totalElementos", resultado.getTotalElements());
            response.put("tamanioPagina", resultado.getSize());
            response.put("esUltimaPagina", resultado.isLast());
            response.put("esPrimeraPagina", resultado.isFirst());

            log.info("GET /api/v1/notificaciones: {} resultados de {} totales",
                    resultado.getNumberOfElements(), resultado.getTotalElements());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error en GET /api/v1/notificaciones", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Error consultando notificaciones: " + e.getMessage()));
        }
    }

    // =========================================================================
    // GET /api/v1/notificaciones/estadisticas
    // =========================================================================

    /**
     * Retorna métricas agregadas de notificaciones.
     *
     * <p>Incluye conteos por tipo de evento, empresa y tipo_notificacion,
     * así como un detalle combinado empresa + tipo_notificacion.</p>
     *
     * <p>IMPORTANTE: Este endpoint debe estar mapeado ANTES de {@code /{id}} para que
     * Spring MVC no intente resolver "estadisticas" como un ID numérico.</p>
     *
     * @param apiKeyHeader API Key en header X-API-Key (requerido)
     * @return Métricas agregadas, o HTTP 401 si API Key inválida
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<?> obtenerEstadisticas(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader) {

        // Requirement 13.1, 13.5: Validar API Key
        if (!esApiKeyValida(apiKeyHeader)) {
            log.warn("Acceso denegado a GET /api/v1/notificaciones/estadisticas: API Key inválida o ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("API Key inválida o ausente. Incluya el header X-API-Key."));
        }

        log.info("GET /api/v1/notificaciones/estadisticas");

        try {
            EstadisticasNotificacionDTO estadisticas = consultarNotificacionesUseCase.obtenerEstadisticas();
            return ResponseEntity.ok(estadisticas);

        } catch (Exception e) {
            log.error("Error en GET /api/v1/notificaciones/estadisticas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Error calculando estadísticas: " + e.getMessage()));
        }
    }

    // =========================================================================
    // GET /api/v1/notificaciones/{id}
    // =========================================================================

    /**
     * Retorna el detalle completo de una notificación específica, incluyendo
     * el JSON completo del evento SNS en el campo {@code informeJson}.
     *
     * @param apiKeyHeader API Key en header X-API-Key (requerido)
     * @param id           ID de la notificación (n_secuencial)
     * @return Detalle de la notificación, HTTP 404 si no existe, o HTTP 401 si API Key inválida
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerDetalle(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @PathVariable Integer id) {

        // Requirement 13.1, 13.5: Validar API Key
        if (!esApiKeyValida(apiKeyHeader)) {
            log.warn("Acceso denegado a GET /api/v1/notificaciones/{}: API Key inválida o ausente", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("API Key inválida o ausente. Incluya el header X-API-Key."));
        }

        log.info("GET /api/v1/notificaciones/{}", id);

        if (id == null || id <= 0) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("El ID de la notificación debe ser un número positivo."));
        }

        try {
            NotificacionDetalleDTO detalle = consultarNotificacionesUseCase.obtenerDetalle(id);
            return ResponseEntity.ok(detalle);

        } catch (NotificacionNotFoundException e) {
            log.warn("Notificación no encontrada: id={}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorResponse("Notificación no encontrada con ID: " + id));

        } catch (Exception e) {
            log.error("Error en GET /api/v1/notificaciones/{}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Error obteniendo detalle de notificación: " + e.getMessage()));
        }
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Valida que el API Key del header coincida con el configurado.
     *
     * <p>La comparación es sensible a mayúsculas/minúsculas.
     * Si {@code mscorreos.api.key} no está configurado (vacío), todas las solicitudes
     * son rechazadas para evitar acceso no autorizado por configuración incompleta.</p>
     *
     * @param apiKeyHeader Valor del header X-API-Key recibido en la solicitud
     * @return {@code true} si el API Key es válido
     */
    private boolean esApiKeyValida(String apiKeyHeader) {
        if (apiKeyHeader == null || apiKeyHeader.trim().isEmpty()) {
            return false;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("mscorreos.api.key no está configurado. Rechazando todas las solicitudes.");
            return false;
        }
        return apiKey.equals(apiKeyHeader.trim());
    }

    /**
     * Construye un mapa de error estándar para respuestas de error.
     *
     * @param mensaje Mensaje de error descriptivo
     * @return Mapa con campos "error" y "mensaje"
     */
    private Map<String, String> errorResponse(String mensaje) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "true");
        error.put("mensaje", mensaje);
        return error;
    }
}
