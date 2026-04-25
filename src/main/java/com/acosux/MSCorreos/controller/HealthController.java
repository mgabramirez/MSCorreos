package com.acosux.MSCorreos.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Controlador REST para verificar el estado de salud del servicio MSCorreos.
 *
 * <p>Expone el siguiente endpoint:</p>
 * <ul>
 *   <li>{@code GET /health} — Retorna HTTP 200 si el servicio está operativo,
 *       HTTP 503 si hay problemas de conectividad</li>
 * </ul>
 *
 * <p>Este endpoint es público y NO requiere autenticación mediante API Key,
 * ya que es utilizado por los health checks de infraestructura (ECS, load balancer).</p>
 *
 * <p>Requirements: 12.3, NF2.2</p>
 *
 * @author MSCorreos Team
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    /** Timeout en segundos para verificar la conectividad a la base de datos. */
    private static final int DB_VALIDATION_TIMEOUT_SECONDS = 5;

    private final DataSource dataSource;

    @Autowired
    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // =========================================================================
    // GET /health
    // =========================================================================

    /**
     * Verifica el estado de salud del servicio.
     *
     * <p>Comprueba la conectividad a la base de datos usando
     * {@link Connection#isValid(int)} con un timeout de
     * {@value #DB_VALIDATION_TIMEOUT_SECONDS} segundos.</p>
     *
     * <p>Respuesta cuando el servicio está operativo (HTTP 200):</p>
     * <pre>
     * {
     *   "status": "UP",
     *   "database": "UP"
     * }
     * </pre>
     *
     * <p>Respuesta cuando hay problemas (HTTP 503):</p>
     * <pre>
     * {
     *   "status": "DOWN",
     *   "database": "DOWN",
     *   "error": "mensaje de error"
     * }
     * </pre>
     *
     * @return HTTP 200 con estado UP si todo está operativo,
     *         HTTP 503 con estado DOWN si hay problemas de conectividad
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();

        boolean dbOk = verificarConectividadBD(response);

        if (dbOk) {
            response.put("status", "UP");
            response.put("database", "UP");
            log.info("Health check: OK");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "DOWN");
            response.put("database", "DOWN");
            log.warn("Health check: FAILED - base de datos no disponible");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    /**
     * Verifica la conectividad a la base de datos obteniendo una conexión del
     * {@link DataSource} y llamando a {@link Connection#isValid(int)}.
     *
     * <p>La conexión se cierra siempre en el bloque {@code finally} para
     * devolverla al pool sin fugas.</p>
     *
     * @param response Mapa de respuesta donde se agrega el campo "error" si falla
     * @return {@code true} si la base de datos es accesible y la conexión es válida
     */
    private boolean verificarConectividadBD(Map<String, Object> response) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            boolean valid = connection.isValid(DB_VALIDATION_TIMEOUT_SECONDS);
            if (!valid) {
                response.put("error", "La conexión a la base de datos no es válida (isValid retornó false)");
                log.warn("Health check BD: isValid() retornó false");
            }
            return valid;
        } catch (Exception e) {
            String errorMsg = "No se pudo conectar a la base de datos: " + e.getMessage();
            response.put("error", errorMsg);
            log.error("Health check BD: error de conectividad", e);
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    log.warn("Health check BD: error al cerrar conexión", e);
                }
            }
        }
    }
}
