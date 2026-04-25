package com.acosux.MSCorreos.config;

import com.acosux.MSCorreos.security.ApiKeyAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de Spring Security para MSCorreos (Spring Boot 3 / Spring Security 6).
 *
 * <p>Endpoints públicos (sin autenticación):</p>
 * <ul>
 *   <li>{@code /health} — Health check para infraestructura (ECS, load balancer)</li>
 *   <li>{@code /api/v1/sns/**} — Endpoint SNS (autenticado por firma SNS, no por API Key)</li>
 * </ul>
 *
 * <p>Endpoints protegidos (requieren API Key en header {@code X-API-Key}):</p>
 * <ul>
 *   <li>{@code /api/v1/notificaciones/**}</li>
 *   <li>{@code /api/v1/lista-negra/**}</li>
 * </ul>
 *
 * <p>Requirements: 13.1, 13.5</p>
 *
 * @see ApiKeyAuthFilter
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configura la cadena de filtros de seguridad HTTP.
     *
     * @param http           Configurador de seguridad HTTP
     * @param apiKeyAuthFilter Filtro de autenticación por API Key
     * @return SecurityFilterChain configurado
     * @throws Exception Si ocurre un error durante la configuración
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        http
            // CSRF deshabilitado: API REST stateless, no usa cookies de sesión
            .csrf(AbstractHttpConfigurer::disable)

            // Sesión stateless: no crear ni usar HttpSession
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Reglas de autorización por URL
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos: no requieren autenticación
                .requestMatchers("/health").permitAll()
                .requestMatchers("/api/v1/sns/**").permitAll()
                // Endpoints protegidos: requieren autenticación (validada por ApiKeyAuthFilter)
                .requestMatchers("/api/v1/notificaciones/**").authenticated()
                .requestMatchers("/api/v1/lista-negra/**").authenticated()
                // Cualquier otra solicitud requiere autenticación por defecto
                .anyRequest().authenticated()
            )

            // Registrar el filtro de API Key antes del filtro de autenticación estándar
            .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // Deshabilitar autenticación HTTP Basic y formulario de login
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
