package com.acosux.MSCorreos.controller;

import com.acosux.MSCorreos.dto.BlacklistEmailResponse;
import com.acosux.MSCorreos.entidades.BlacklistEmail;
import com.acosux.MSCorreos.exception.EmailYaExisteException;
import com.acosux.MSCorreos.exception.EmailNoEncontradoException;
import com.acosux.MSCorreos.service.BlacklistEmailService;
import com.acosux.MSCorreos.util.RespuestaWebTO;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para gestionar la blacklist de correos electrónicos.
 * Este componente es GLOBAL, no está asociado a ninguna empresa.
 */
@RestController
@RequestMapping("/api/v1/blacklist-emails/")
public class BlacklistEmailController {
    
    @Autowired
    private BlacklistEmailService blacklistService;
    
    /**
     * GET /api/v1/blacklist-emails/check/{email}
     * Verifica si un email está bloqueado.
     */
    @GetMapping("check/{email}")
    public RespuestaWebTO checkEmail(@PathVariable String email) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("email", email);
        datos.put("bloqueado", blacklistService.isEmailBloqueado(email));
        
        RespuestaWebTO respuesta = new RespuestaWebTO();
        respuesta.setEstadoOperacion(RespuestaWebTO.EstadoOperacionEnum.EXITO.getValor());
        respuesta.setExtraInfo(datos);
        return respuesta;
    }
    
    /**
     * POST /api/v1/blacklist-emails
     * Agrega un email a la blacklist.
     * Body: { "email": "correo@ejemplo.com", "razon": "Razón del bloqueo" }
     */
    @PostMapping
    public RespuestaWebTO agregarEmail(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        
        String email = request.get("email");
        String razon = request.get("razon");
        
        if (email == null || email.trim().isEmpty()) {
            RespuestaWebTO respuesta = new RespuestaWebTO();
            respuesta.setEstadoOperacion(RespuestaWebTO.EstadoOperacionEnum.ERROR.getValor());
            respuesta.setOperacionMensaje("El email es requerido");
            return respuesta;
        }
        
        try {
            BlacklistEmail nuevo = blacklistService.agregarEmail(email, razon, usuario);
            
            RespuestaWebTO respuesta = new RespuestaWebTO();
            respuesta.setEstadoOperacion(RespuestaWebTO.EstadoOperacionEnum.EXITO.getValor());
            respuesta.setOperacionMensaje("Email agregado a blacklist");
            respuesta.setExtraInfo(toResponse(nuevo));
            return respuesta;
        } catch (EmailYaExisteException e) {
            RespuestaWebTO respuesta = new RespuestaWebTO();
            respuesta.setEstadoOperacion(RespuestaWebTO.EstadoOperacionEnum.ERROR.getValor());
            respuesta.setOperacionMensaje(e.getMessage());
            return respuesta;
        }
    }
    
    /**
     * DELETE /api/v1/blacklist-emails/{email}
     * Elimina (desactiva) un email de la blacklist.
     * Retorna 204 No Content en caso de éxito.
     */
    @DeleteMapping("{email}")
    public ResponseEntity<Void> eliminarEmail(
            @PathVariable String email,
            @RequestHeader(value = "X-Usuario", required = false) String usuario) {
        
        blacklistService.eliminarEmail(email, usuario);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * GET /api/v1/blacklist-emails
     * Lista emails en blacklist con paginación usando Spring Pageable.
     * Params: page (default 0), size (default 20), sort (default "fechaBloqueo")
     */
    @GetMapping
    public ResponseEntity<Page<BlacklistEmailResponse>> listarEmails(
            @PageableDefault(size = 20, sort = "fechaBloqueo") Pageable pageable) {
        
        Page<BlacklistEmail> emailsPage = blacklistService.listarEmails(pageable);
        Page<BlacklistEmailResponse> responsePage = emailsPage.map(this::toResponse);
        
        return ResponseEntity.ok(responsePage);
    }
    
    /**
     * GET /api/v1/blacklist-emails/count
     * Cuenta los emails activos en la blacklist.
     */
    @GetMapping("count")
    public RespuestaWebTO countEmails() {
        long count = blacklistService.countEmails();
        
        Map<String, Object> datos = new HashMap<>();
        datos.put("count", count);
        
        RespuestaWebTO respuesta = new RespuestaWebTO();
        respuesta.setEstadoOperacion(RespuestaWebTO.EstadoOperacionEnum.EXITO.getValor());
        respuesta.setExtraInfo(datos);
        return respuesta;
    }
    
    /**
     * Método helper para convertir BlacklistEmail a BlacklistEmailResponse DTO.
     * Convierte la entidad del dominio a un objeto de respuesta para la API.
     */
    private BlacklistEmailResponse toResponse(BlacklistEmail email) {
        return new BlacklistEmailResponse(
            email.getId(),
            email.getEmail(),
            email.getRazon(),
            email.getFechaBloqueo(),
            email.getBloqueadoPor(),
            email.getActivo()
        );
    }
    
    /**
     * Maneja excepciones de email no encontrado.
     */
    @ExceptionHandler(EmailNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleEmailNoEncontrado(EmailNoEncontradoException e) {
        Map<String, String> error = new HashMap<>();
        error.put("error", e.getMessage());
        return ResponseEntity.notFound().build();
    }
}