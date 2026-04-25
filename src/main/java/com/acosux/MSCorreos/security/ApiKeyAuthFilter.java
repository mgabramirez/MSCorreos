package com.acosux.MSCorreos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Filtro de autenticación mediante API Key para endpoints protegidos.
 *
 * <p>Valida el header {@code X-API-Key} en cada solicitud que llega a los
 * endpoints protegidos. Si el header está ausente o el valor no coincide con
 * la clave configurada, retorna HTTP 401 Unauthorized.</p>
 *
 * <p>Este filtro se aplica SOLO a los endpoints protegidos configurados en
 * {@link SecurityConfig}. Los endpoints públicos ({@code /health},
 * {@code /api/v1/sns/**}) no pasan por este filtro.</p>
 *
 * <p>La API Key se configura en {@code application.properties} bajo la clave
 * {@code mscorreos.api.key}.</p>
 *
 * <p>Requirements: 13.1, 13.5</p>
 *
 * @author MSCorreos Team
 * @see SecurityConfig
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);

    /** Nombre del header HTTP que contiene la API Key. */
    public static final String API_KEY_HEADER = "X-API-Key";

    /**
     * API Key configurada en application.properties bajo la clave {@code mscorreos.api.key}.
     * Si no está configurada, se usa un valor vacío que rechazará todas las solicitudes.
     */
    @Value("${mscorreos.api.key:}")
    private String configuredApiKey;

    /**
     * Valida el header {@code X-API-Key} de la solicitud entrante.
     *
     * <p>Flujo de validación:</p>
     * <ol>
     *   <li>Lee el header {@code X-API-Key} de la solicitud</li>
     *   <li>Si el header está ausente o vacío → retorna HTTP 401</li>
     *   <li>Si la API Key configurada está vacía → retorna HTTP 401 (configuración incompleta)</li>
     *   <li>Si el header no coincide con la clave configurada → retorna HTTP 401</li>
     *   <li>Si la clave es válida → permite continuar la cadena de filtros</li>
     * </ol>
     *
     * @param request     Solicitud HTTP entrante
     * @param response    Respuesta HTTP
     * @param filterChain Cadena de filtros de Spring Security
     * @throws ServletException Si ocurre un error en el procesamiento del filtro
     * @throws IOException      Si ocurre un error de I/O
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        if (apiKeyHeader == null || apiKeyHeader.trim().isEmpty()) {
            log.warn("Acceso denegado a {}: header {} ausente o vacío",
                    request.getRequestURI(), API_KEY_HEADER);
            enviarRespuesta401(response, "API Key ausente. Incluya el header " + API_KEY_HEADER + ".");
            return;
        }

        if (configuredApiKey == null || configuredApiKey.trim().isEmpty()) {
            log.error("mscorreos.api.key no está configurado. Rechazando solicitud a {}",
                    request.getRequestURI());
            enviarRespuesta401(response, "Servicio no configurado correctamente. Contacte al administrador.");
            return;
        }

        if (!configuredApiKey.equals(apiKeyHeader.trim())) {
            log.warn("Acceso denegado a {}: API Key inválida", request.getRequestURI());
            enviarRespuesta401(response, "API Key inválida.");
            return;
        }

        log.debug("API Key válida para solicitud a {}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    /**
     * Escribe una respuesta HTTP 401 Unauthorized con cuerpo JSON.
     *
     * @param response Respuesta HTTP donde se escribe el error
     * @param mensaje  Mensaje descriptivo del error de autenticación
     * @throws IOException Si ocurre un error al escribir la respuesta
     */
    private void enviarRespuesta401(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"error\":\"true\",\"mensaje\":\"" + mensaje + "\"}"
        );
    }
}
