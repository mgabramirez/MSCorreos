package com.acosux.MSCorreos.controller;

import com.acosux.MSCorreos.application.usecases.GestionarListaNegraUseCase;
import com.acosux.MSCorreos.dtos.AgregarListaNegraRequest;
import com.acosux.MSCorreos.dtos.FiltrosListaNegra;
import com.acosux.MSCorreos.dtos.ListaNegraDTO;
import com.acosux.MSCorreos.entidades.ListaNegraHistorial;
import com.acosux.MSCorreos.enums.TipoBloqueo;
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
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para gestión de la lista negra de correos bloqueados.
 *
 * <p>Expone los siguientes endpoints:</p>
 * <ul>
 *   <li>{@code GET /api/v1/lista-negra} — Lista correos bloqueados con filtros y paginación</li>
 *   <li>{@code POST /api/v1/lista-negra} — Agrega un correo manualmente a la lista negra</li>
 *   <li>{@code DELETE /api/v1/lista-negra/{email}} — Remueve (desactiva) un correo de la lista negra</li>
 *   <li>{@code GET /api/v1/lista-negra/{email}} — Consulta el estado de un correo específico</li>
 *   <li>{@code GET /api/v1/lista-negra/{email}/historial} — Historial de cambios de un correo</li>
 * </ul>
 *
 * <p>Todos los endpoints requieren autenticación mediante API Key en el header
 * {@code X-API-Key}. Las solicitudes sin API Key válida reciben HTTP 401.</p>
 *
 * <p>Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 13.1, 13.5</p>
 *
 * @author MSCorreos Team
 */
@RestController
@RequestMapping("/api/v1/lista-negra")
public class ListaNegraController {

    private static final Logger log = LoggerFactory.getLogger(ListaNegraController.class);

    /** Tamaño máximo de página permitido. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Tamaño de página por defecto. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final GestionarListaNegraUseCase gestionarListaNegraUseCase;

    /**
     * API Key configurada en application.properties bajo la clave {@code mscorreos.api.key}.
     * Si no está configurada, se usa un valor vacío que rechazará todas las solicitudes.
     */
    @Value("${mscorreos.api.key:}")
    private String apiKey;

    @Autowired
    public ListaNegraController(GestionarListaNegraUseCase gestionarListaNegraUseCase) {
        this.gestionarListaNegraUseCase = gestionarListaNegraUseCase;
    }

    // =========================================================================
    // GET /api/v1/lista-negra
    // =========================================================================

    /**
     * Lista correos bloqueados con filtros opcionales y paginación.
     *
     * <p>Filtros disponibles (todos opcionales):</p>
     * <ul>
     *   <li>{@code tipoBloqueo} — Filtro por tipo: HARD_BOUNCE, SOFT_BOUNCE_REPETIDO, COMPLAINT, MANUAL</li>
     *   <li>{@code activo} — Filtro por estado activo/inactivo</li>
     *   <li>{@code fechaDesde} — Fecha de inicio del rango de registro (formato ISO: yyyy-MM-dd)</li>
     *   <li>{@code fechaHasta} — Fecha de fin del rango de registro (formato ISO: yyyy-MM-dd)</li>
     * </ul>
     *
     * <p>Parámetros de paginación:</p>
     * <ul>
     *   <li>{@code page} — Número de página (0-based, default 0)</li>
     *   <li>{@code size} — Tamaño de página (default 20, máximo 100)</li>
     * </ul>
     *
     * @param apiKeyHeader API Key en header X-API-Key (requerido)
     * @param tipoBloqueo  Filtro por tipo de bloqueo
     * @param activo       Filtro por estado activo
     * @param fechaDesde   Fecha de inicio del rango
     * @param fechaHasta   Fecha de fin del rango
     * @param page         Número de página (default 0)
     * @param size         Tamaño de página (default 20, máximo 100)
     * @return Página de correos bloqueados con metadatos de paginación, o HTTP 401 si API Key inválida
     */
    @GetMapping
    public ResponseEntity<?> listarListaNegra(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @RequestParam(required = false) String tipoBloqueo,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) Date fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Requirement 13.1, 13.5: Validar API Key
        if (!esApiKeyValida(apiKeyHeader)) {
            log.warn("Acceso denegado a GET /api/v1/lista-negra: API Key inválida o ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("API Key inválida o ausente. Incluya el header X-API-Key."));
        }

        // Validar tipoBloqueo si se proporcionó
        if (tipoBloqueo != null && !tipoBloqueo.isEmpty()) {
            try {
                TipoBloqueo.valueOf(tipoBloqueo.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(errorResponse("Tipo de bloqueo inválido: " + tipoBloqueo +
                                ". Valores válidos: HARD_BOUNCE, SOFT_BOUNCE_REPETIDO, COMPLAINT, MANUAL"));
            }
        }

        // Limitar tamaño de página
        int pageSize = Math.min(size, MAX_PAGE_SIZE);
        if (pageSize <= 0) {
            pageSize = DEFAULT_PAGE_SIZE;
        }

        log.info("GET /api/v1/lista-negra: tipoBloqueo={}, activo={}, fechaDesde={}, fechaHasta={}, page={}, size={}",
                tipoBloqueo, activo, fechaDesde, fechaHasta, page, pageSize);

        FiltrosListaNegra filtros = FiltrosListaNegra.builder()
                .tipoBloqueo(tipoBloqueo != null ? tipoBloqueo.toUpperCase() : null)
                .activo(activo)
                .fechaDesde(fechaDesde)
                .fechaHasta(fechaHasta)
                .build();

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "fechaRegistro"));

        try {
            Page<ListaNegraDTO> resultado = gestionarListaNegraUseCase.consultar(filtros, pageable);

            Map<String, Object> response = new HashMap<>();
            response.put("contenido", resultado.getContent());
            response.put("paginaActual", resultado.getNumber());
            response.put("totalPaginas", resultado.getTotalPages());
            response.put("totalElementos", resultado.getTotalElements());
            response.put("tamanioPagina", resultado.getSize());
            response.put("esUltimaPagina", resultado.isLast());
            response.put("esPrimeraPagina", resultado.isFirst());

            log.info("GET /api/v1/lista-negra: {} resultados de {} totales",
                    resultado.getNumberOfElements(), resultado.getTotalElements());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error en GET /api/v1/lista-negra", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Error consultando lista negra: " + e.getMessage()));
        }
    }

    // =========================================================================
    // POST /api/v1/lista-negra
    // =========================================================================

    /**
     * Agrega un correo manualmente a la lista negra con tipo_bloqueo MANUAL.
     *
     * <p>El cuerpo de la solicitud debe incluir:</p>
     * <ul>
     *   <li>{@code email} — Dirección de correo a bloquear (requerido)</li>
     *   <li>{@code motivo} — Motivo del bloqueo (requerido)</li>
     *   <li>{@code tipoBloqueo} — Tipo de bloqueo (opcional, default MANUAL)</li>
     * </ul>
     *
     * @param apiKeyHeader API Key en header X-API-Key (requerido)
     * @param request      Datos del correo a agregar
     * @return HTTP 201 si se agregó correctamente, HTTP 400 si datos inválidos, HTTP 401 si API Key inválida
     */
    @PostMapping
    public ResponseEntity<?> agregarAListaNegra(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @RequestBody AgregarListaNegraRequest request) {

        // Requirement 13.1, 13.5: Validar API Key
        if (!esApiKeyValida(apiKeyHeader)) {
            log.warn("Acceso denegado a POST /api/v1/lista-negra: API Key inválida o ausente");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("API Key inválida o ausente. Incluya el header X-API-Key."));
        }

        // Validar campos requeridos
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("El cuerpo de la solicitud es requerido."));
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("El campo 'email' es requerido."));
        }
        if (request.getMotivo() == null || request.getMotivo().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("El campo 'motivo' es requerido."));
        }

        // Determinar tipo de bloqueo (default MANUAL)
        TipoBloqueo tipo = TipoBloqueo.MANUAL;
        if (request.getTipoBloqueo() != null && !request.getTipoBloqueo().isEmpty()) {
            try {
                tipo = TipoBloqueo.valueOf(request.getTipoBloqueo().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(errorResponse("Tipo de bloqueo inválido: " + request.getTipoBloqueo() +
                                ". Valores válidos: HARD_BOUNCE, SOFT_BOUNCE_REPETIDO, COMPLAINT, MANUAL"));
            }
        }

        String emailNormalizado = request.getEmail().trim().toLowerCase();
        log.info("POST /api/v1/lista-negra: email={}, tipo={}", emailNormalizado, tipo);

        try {
            gestionarListaNegraUseCase.agregar(emailNormalizado, request.getMotivo().trim(), tipo);

            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Email agregado a la lista negra correctamente.");
            response.put("email", emailNormalizado);
            response.put("tipoBloqueo", tipo.name());

            log.info("Email agregado a lista negra: email={}, tipo={}", emailNormalizado, tipo);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Datos inválidos al agregar a lista negra: email={}, error={}", emailNormalizado, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(errorResponse("Datos inválidos: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Error en POST /api/v1/lista-negra: email={}", emailNormalizado, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Error agregando email a lista negra: " + e.getMessage()));
        }
    }

    // =========================================================================
    // DELETE /api/v1/lista-negra/{email}
    // =========================================================================

    /**
     * Remueve (desactiva) un correo de la lista negra.
     *
     * <p>La operación es un soft-delete: el registro permanece en la base de datos
     * con {@code activo = false} para mantener el historial de auditoría.</p>
     *
     * @param apiKeyHeader API Key en header X-API-Key (requerido)
     * @param email        Dirección de correo a remover (URL-encoded si contiene caracteres especiales)
     * @param motivo       Motivo de la remoción (query param, requerido)
     * @param usuario      Usuario que realiza la acción (query param, opcional)
     * @return HTTP 200 si se removió correctamente, HTTP 400 si datos inválidos, HTTP 401 si API Key inválida
     */
    @DeleteMapping("/{email}")
    public ResponseEntity<?> removerDeListaNegra(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @PathVariable String email,
            @RequestParam(required = false) String motivo,
            @RequestParam(required = false) String usuario) {

        // Requirement 13.1, 13.5: Validar API Key
        if (!esApiKeyValida(apiKeyHeader)) {
            log.warn("Acceso denegado a DELETE /api/v1/lista-negra/{}: API Key inválida o ausente", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("API Key inválida o ausente. Incluya el header X-API-Key."));
        }

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("El email es requerido en la URL."));
        }
        if (motivo == null || motivo.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("El parámetro 'motivo' es requerido."));
        }

        String emailNormalizado = email.trim().toLowerCase();
        log.info("DELETE /api/v1/lista-negra/{}: usuario={}", emailNormalizado, usuario);

        try {
            gestionarListaNegraUseCase.remover(emailNormalizado, motivo.trim(), usuario);

            Map<String, Object> response = new HashMap<>();
            response.put("mensaje", "Email removido de la lista negra correctamente.");
            response.put("email", emailNormalizado);

            log.info("Email removido de lista negra: email={}, usuario={}", emailNormalizado, usuario);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Email no encontrado en lista negra: email={}", emailNormalizado);
            return ResponseEntity.badRequest()
                    .body(errorResponse("Error al remover email: " + e.getMessage()));

        } catch (Exception e) {
            log.error("Error en DELETE /api/v1/lista-negra/{}", emailNormalizado, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Error removiendo email de lista negra: " + e.getMessage()));
        }
    }

    // =========================================================================
    // GET /api/v1/lista-negra/{email}
    // =========================================================================

    /**
     * Consulta el estado actual de un correo específico en la lista negra.
     *
     * <p>Retorna el estado del correo (bloqueado/no bloqueado) junto con los
     * detalles del registro si existe en la lista negra.</p>
     *
     * @param apiKeyHeader API Key en header X-API-Key (requerido)
     * @param email        Dirección de correo a consultar
     * @return Estado del correo con detalles, o HTTP 401 si API Key inválida
     */
    @GetMapping("/{email}")
    public ResponseEntity<?> consultarEstado(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @PathVariable String email) {

        // Requirement 13.1, 13.5: Validar API Key
        if (!esApiKeyValida(apiKeyHeader)) {
            log.warn("Acceso denegado a GET /api/v1/lista-negra/{}: API Key inválida o ausente", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("API Key inválida o ausente. Incluya el header X-API-Key."));
        }

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("El email es requerido en la URL."));
        }

        String emailNormalizado = email.trim().toLowerCase();
        log.info("GET /api/v1/lista-negra/{}", emailNormalizado);

        try {
            boolean bloqueado = gestionarListaNegraUseCase.estaEnListaNegra(emailNormalizado);

            // Obtener historial para mostrar detalles del último cambio
            List<ListaNegraHistorial> historial = gestionarListaNegraUseCase.obtenerHistorial(emailNormalizado);

            Map<String, Object> response = new HashMap<>();
            response.put("email", emailNormalizado);
            response.put("bloqueado", bloqueado);

            if (!historial.isEmpty()) {
                // Obtener el registro más reciente del historial para mostrar detalles
                ListaNegraHistorial ultimoCambio = historial.get(0);
                response.put("ultimaAccion", ultimoCambio.getAccion() != null ? ultimoCambio.getAccion().name() : null);
                response.put("ultimaFechaCambio", ultimoCambio.getFecha());
                response.put("ultimoMotivo", ultimoCambio.getMotivo());
            }

            log.info("GET /api/v1/lista-negra/{}: bloqueado={}", emailNormalizado, bloqueado);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error en GET /api/v1/lista-negra/{}", emailNormalizado, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Error consultando estado del email: " + e.getMessage()));
        }
    }

    // =========================================================================
    // GET /api/v1/lista-negra/{email}/historial
    // =========================================================================

    /**
     * Retorna el historial completo de cambios de un correo en la lista negra.
     *
     * <p>El historial incluye todas las acciones (AGREGAR/REMOVER) realizadas
     * sobre el correo, ordenadas por fecha descendente.</p>
     *
     * @param apiKeyHeader API Key en header X-API-Key (requerido)
     * @param email        Dirección de correo a consultar
     * @return Lista de cambios históricos, o HTTP 401 si API Key inválida
     */
    @GetMapping("/{email}/historial")
    public ResponseEntity<?> obtenerHistorial(
            @RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            @PathVariable String email) {

        // Requirement 13.1, 13.5: Validar API Key
        if (!esApiKeyValida(apiKeyHeader)) {
            log.warn("Acceso denegado a GET /api/v1/lista-negra/{}/historial: API Key inválida o ausente", email);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorResponse("API Key inválida o ausente. Incluya el header X-API-Key."));
        }

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(errorResponse("El email es requerido en la URL."));
        }

        String emailNormalizado = email.trim().toLowerCase();
        log.info("GET /api/v1/lista-negra/{}/historial", emailNormalizado);

        try {
            List<ListaNegraHistorial> historial = gestionarListaNegraUseCase.obtenerHistorial(emailNormalizado);

            Map<String, Object> response = new HashMap<>();
            response.put("email", emailNormalizado);
            response.put("totalCambios", historial.size());
            response.put("historial", historial);

            log.info("GET /api/v1/lista-negra/{}/historial: {} registros", emailNormalizado, historial.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error en GET /api/v1/lista-negra/{}/historial", emailNormalizado, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse("Error obteniendo historial del email: " + e.getMessage()));
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
