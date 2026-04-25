package com.acosux.MSCorreos.application.usecases;

import com.acosux.MSCorreos.dtos.FiltrosListaNegra;
import com.acosux.MSCorreos.dtos.ListaNegraDTO;
import com.acosux.MSCorreos.entidades.AccionListaNegra;
import com.acosux.MSCorreos.entidades.ListaNegra;
import com.acosux.MSCorreos.entidades.ListaNegraHistorial;
import com.acosux.MSCorreos.enums.TipoBloqueo;
import com.acosux.MSCorreos.repositories.ListaNegraHistorialRepository;
import com.acosux.MSCorreos.repositories.ListaNegraRepository;
import com.acosux.MSCorreos.valueobjects.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementación del caso de uso para gestionar la lista negra de correos bloqueados.
 * 
 * Esta implementación:
 * - Valida emails usando EmailAddress value object
 * - Registra todas las acciones en historial para auditoría
 * - Usa cache Caffeine con TTL 5 minutos para consultas de bloqueo
 * - Aplica transacciones para garantizar consistencia de datos
 * - Implementa soft delete (activo=false) en lugar de eliminación física
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6
 * 
 * @author MSCorreos Team
 */
@Service
@Transactional
public class GestionarListaNegraUseCaseImpl implements GestionarListaNegraUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(GestionarListaNegraUseCaseImpl.class);
    
    private final ListaNegraRepository listaNegraRepository;
    private final ListaNegraHistorialRepository listaNegraHistorialRepository;
    
    @Autowired
    public GestionarListaNegraUseCaseImpl(
            ListaNegraRepository listaNegraRepository,
            ListaNegraHistorialRepository listaNegraHistorialRepository) {
        this.listaNegraRepository = listaNegraRepository;
        this.listaNegraHistorialRepository = listaNegraHistorialRepository;
    }
    
    /**
     * Agrega un email a la lista negra con registro en historial.
     * 
     * Si el email ya existe:
     * - Actualiza activo=true y tipo_bloqueo
     * Si no existe:
     * - Crea nuevo registro con fecha_registro=now
     * 
     * Siempre registra la acción en cor_lista_negra_historial e invalida cache.
     * 
     * @param email Email a bloquear
     * @param motivo Motivo del bloqueo
     * @param tipo Tipo de bloqueo (HARD_BOUNCE, SOFT_BOUNCE_REPETIDO, COMPLAINT, MANUAL)
     * @throws IllegalArgumentException si el email es inválido
     */
    @Override
    @CacheEvict(value = "listaNegraCache", key = "#email")
    public void agregar(String email, String motivo, TipoBloqueo tipo) {
        log.info("Agregando email a lista negra: email={}, tipo={}", email, tipo);
        
        try {
            // Validar email usando EmailAddress value object
            EmailAddress emailAddress = new EmailAddress(email);
            String emailNormalizado = emailAddress.getValue();
            
            // Convertir TipoBloqueo de enums a entidades
            com.acosux.MSCorreos.entidades.TipoBloqueo tipoEntidad = convertirTipoBloqueo(tipo);
            
            // Buscar si el email ya existe en la lista negra
            Optional<ListaNegra> existente = listaNegraRepository.findByEmail(emailNormalizado);
            
            ListaNegra listaNegra;
            
            if (existente.isPresent()) {
                // Email ya existe: actualizar activo=true y tipo_bloqueo
                listaNegra = existente.get();
                listaNegra.setActivo(true);
                listaNegra.setTipoBloqueo(tipoEntidad);
                listaNegra.setMotivo(motivo);
                
                log.debug("Email ya existía en lista negra, actualizando: id={}", listaNegra.getId());
            } else {
                // Email no existe: crear nuevo registro
                listaNegra = new ListaNegra();
                listaNegra.setEmail(emailNormalizado);
                listaNegra.setMotivo(motivo);
                listaNegra.setTipoBloqueo(tipoEntidad);
                listaNegra.setActivo(true);
                listaNegra.setFechaRegistro(new Date());
                listaNegra.setContadorSoftBounce(0);
                
                log.debug("Creando nuevo registro en lista negra para: {}", emailNormalizado);
            }
            
            // Guardar en base de datos
            listaNegra = listaNegraRepository.save(listaNegra);
            
            // Registrar acción en historial (usuario=null para acciones automáticas)
            registrarEnHistorial(emailNormalizado, AccionListaNegra.AGREGAR, motivo, null);
            
            log.info("Email agregado exitosamente a lista negra: email={}, id={}, tipo={}", 
                    emailNormalizado, listaNegra.getId(), tipo);
            
        } catch (IllegalArgumentException e) {
            log.error("Email inválido al agregar a lista negra: {}", email, e);
            throw e;
        } catch (Exception e) {
            log.error("Error agregando email a lista negra: {}", email, e);
            throw new RuntimeException("Error agregando email a lista negra: " + e.getMessage(), e);
        }
    }
    
    /**
     * Remueve un email de la lista negra (desactiva) con registro en historial.
     * 
     * Implementa soft delete: marca activo=false en lugar de eliminar el registro.
     * Esto preserva el historial y permite auditoría completa.
     * 
     * @param email Email a desbloquear
     * @param motivo Motivo de la remoción
     * @param usuario Usuario que realiza la acción (NULL para acciones automáticas)
     * @throws IllegalArgumentException si el email no existe en la lista negra
     */
    @Override
    @CacheEvict(value = "listaNegraCache", key = "#email")
    public void remover(String email, String motivo, String usuario) {
        log.info("Removiendo email de lista negra: email={}, usuario={}", email, usuario);
        
        try {
            // Validar email usando EmailAddress value object
            EmailAddress emailAddress = new EmailAddress(email);
            String emailNormalizado = emailAddress.getValue();
            
            // Buscar email en lista negra
            Optional<ListaNegra> existente = listaNegraRepository.findByEmail(emailNormalizado);
            
            if (!existente.isPresent()) {
                String mensaje = "Email no encontrado en lista negra: " + emailNormalizado;
                log.warn(mensaje);
                throw new IllegalArgumentException(mensaje);
            }
            
            ListaNegra listaNegra = existente.get();
            
            // Soft delete: marcar como inactivo
            listaNegra.setActivo(false);
            listaNegraRepository.save(listaNegra);
            
            // Registrar acción en historial
            registrarEnHistorial(emailNormalizado, AccionListaNegra.REMOVER, motivo, usuario);
            
            log.info("Email removido exitosamente de lista negra: email={}, id={}, usuario={}", 
                    emailNormalizado, listaNegra.getId(), usuario);
            
        } catch (IllegalArgumentException e) {
            log.error("Error al remover email de lista negra: {}", email, e);
            throw e;
        } catch (Exception e) {
            log.error("Error removiendo email de lista negra: {}", email, e);
            throw new RuntimeException("Error removiendo email de lista negra: " + e.getMessage(), e);
        }
    }
    
    /**
     * Verifica si un email está en lista negra (con cache).
     * 
     * Usa Caffeine cache con TTL 5 minutos para optimizar consultas frecuentes.
     * Para SOFT_BOUNCE_REPETIDO: verifica que ultimo_soft_bounce esté dentro de 30 días.
     * 
     * @param email Email a verificar
     * @return true si está bloqueado y activo, false en caso contrario
     */
    @Override
    @Cacheable(value = "listaNegraCache", key = "#email")
    @Transactional(readOnly = true)
    public boolean estaEnListaNegra(String email) {
        log.debug("Verificando si email está en lista negra: {}", email);
        
        try {
            // Validar email usando EmailAddress value object
            EmailAddress emailAddress = new EmailAddress(email);
            String emailNormalizado = emailAddress.getValue();
            
            // Consultar cor_lista_negra WHERE email=? AND activo=true
            Optional<ListaNegra> listaNegra = listaNegraRepository.findByEmailAndActivoTrue(emailNormalizado);
            
            if (!listaNegra.isPresent()) {
                log.debug("Email no está en lista negra: {}", emailNormalizado);
                return false;
            }
            
            // Para SOFT_BOUNCE_REPETIDO: verificar si ultimo_soft_bounce está dentro de 30 días
            boolean bloqueado = listaNegra.get().debeSerBloqueado();
            
            log.debug("Email en lista negra: email={}, bloqueado={}, tipo={}", 
                    emailNormalizado, bloqueado, listaNegra.get().getTipoBloqueo());
            
            return bloqueado;
            
        } catch (IllegalArgumentException e) {
            // Email inválido: no está en lista negra
            log.debug("Email inválido al verificar lista negra: {}", email);
            return false;
        } catch (Exception e) {
            log.error("Error verificando email en lista negra: {}", email, e);
            // En caso de error, por seguridad retornar false (no bloquear)
            return false;
        }
    }
    
    /**
     * Consulta lista negra con filtros y paginación.
     * 
     * Aplica filtros usando métodos del repositorio:
     * - tipoBloqueo: filtra por tipo de bloqueo
     * - fechaDesde/fechaHasta: filtra por rango de fecha_registro
     * - activo: filtra por estado activo/inactivo
     * 
     * @param filtros Filtros de búsqueda (tipo_bloqueo, activo, fecha_desde, fecha_hasta)
     * @param pageable Configuración de paginación
     * @return Página de emails bloqueados que cumplen con los filtros
     */
    @Override
    @Transactional(readOnly = true)
    public Page<ListaNegraDTO> consultar(FiltrosListaNegra filtros, Pageable pageable) {
        log.debug("Consultando lista negra con filtros: tipoBloqueo={}, activo={}, fechaDesde={}, fechaHasta={}", 
                filtros.getTipoBloqueo(), filtros.getActivo(), filtros.getFechaDesde(), filtros.getFechaHasta());
        
        try {
            Page<ListaNegra> resultado;
            
            // Aplicar filtros según los parámetros proporcionados
            if (filtros.getTipoBloqueo() != null && !filtros.getTipoBloqueo().isEmpty()) {
                // Filtro por tipo_bloqueo
                try {
                    com.acosux.MSCorreos.entidades.TipoBloqueo tipo = 
                            com.acosux.MSCorreos.entidades.TipoBloqueo.valueOf(filtros.getTipoBloqueo());
                    resultado = listaNegraRepository.findByTipoBloqueo(tipo, pageable);
                } catch (IllegalArgumentException e) {
                    log.warn("Tipo de bloqueo inválido en filtros: {}", filtros.getTipoBloqueo());
                    // Retornar página vacía si el tipo es inválido
                    return new PageImpl<>(new ArrayList<>(), pageable, 0);
                }
            } else if (filtros.getFechaDesde() != null && filtros.getFechaHasta() != null) {
                // Filtro por rango de fechas
                resultado = listaNegraRepository.findByFechaRegistroBetween(
                        filtros.getFechaDesde(), filtros.getFechaHasta(), pageable);
            } else if (filtros.getActivo() != null && filtros.getActivo()) {
                // Filtro solo activos
                resultado = listaNegraRepository.findByActivoTrue(pageable);
            } else {
                // Sin filtros específicos: retornar todos
                resultado = listaNegraRepository.findAll(pageable);
            }
            
            // Aplicar filtro adicional de activo si está presente y no se usó antes
            List<ListaNegra> contenidoFiltrado = resultado.getContent();
            if (filtros.getActivo() != null && 
                (filtros.getTipoBloqueo() != null || filtros.getFechaDesde() != null)) {
                contenidoFiltrado = contenidoFiltrado.stream()
                        .filter(ln -> ln.getActivo().equals(filtros.getActivo()))
                        .collect(Collectors.toList());
            }
            
            // Convertir entidades a DTOs
            List<ListaNegraDTO> dtos = contenidoFiltrado.stream()
                    .map(this::convertirADTO)
                    .collect(Collectors.toList());
            
            log.debug("Consulta de lista negra completada: total={}, página={}, tamaño={}", 
                    resultado.getTotalElements(), pageable.getPageNumber(), resultado.getSize());
            
            return new PageImpl<>(dtos, pageable, resultado.getTotalElements());
            
        } catch (Exception e) {
            log.error("Error consultando lista negra con filtros", e);
            throw new RuntimeException("Error consultando lista negra: " + e.getMessage(), e);
        }
    }
    
    /**
     * Obtiene historial de cambios de un email específico.
     * 
     * Consulta cor_lista_negra_historial WHERE email=? ORDER BY fecha DESC.
     * Retorna todos los cambios históricos para auditoría completa.
     * 
     * @param email Email a consultar
     * @return Lista de cambios históricos ordenados por fecha descendente
     */
    @Override
    @Transactional(readOnly = true)
    public List<ListaNegraHistorial> obtenerHistorial(String email) {
        log.debug("Obteniendo historial de lista negra para email: {}", email);
        
        try {
            // Validar email usando EmailAddress value object
            EmailAddress emailAddress = new EmailAddress(email);
            String emailNormalizado = emailAddress.getValue();
            
            // Consultar historial ordenado por fecha descendente
            List<ListaNegraHistorial> historial = listaNegraHistorialRepository
                    .findByEmailOrderByFechaDesc(emailNormalizado);
            
            log.debug("Historial obtenido: email={}, registros={}", emailNormalizado, historial.size());
            
            return historial;
            
        } catch (IllegalArgumentException e) {
            log.warn("Email inválido al obtener historial: {}", email);
            // Retornar lista vacía para email inválido
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error obteniendo historial de lista negra: {}", email, e);
            throw new RuntimeException("Error obteniendo historial: " + e.getMessage(), e);
        }
    }
    
    /**
     * Registra una acción en el historial de lista negra.
     * 
     * @param email Email afectado
     * @param accion Acción realizada (AGREGAR o REMOVER)
     * @param motivo Motivo de la acción
     * @param usuario Usuario que realizó la acción (NULL para acciones automáticas)
     */
    private void registrarEnHistorial(String email, AccionListaNegra accion, String motivo, String usuario) {
        try {
            ListaNegraHistorial historial = new ListaNegraHistorial();
            historial.setEmail(email);
            historial.setAccion(accion);
            historial.setMotivo(motivo);
            historial.setUsuario(usuario);
            historial.setFecha(new Date());
            
            listaNegraHistorialRepository.save(historial);
            
            log.debug("Acción registrada en historial: email={}, accion={}, usuario={}", 
                    email, accion, usuario);
            
        } catch (Exception e) {
            log.error("Error registrando acción en historial: email={}, accion={}", email, accion, e);
            // No lanzar excepción para no interrumpir el flujo principal
        }
    }
    
    /**
     * Convierte TipoBloqueo del paquete enums al paquete entidades.
     * 
     * @param tipo TipoBloqueo del paquete enums
     * @return TipoBloqueo del paquete entidades
     */
    private com.acosux.MSCorreos.entidades.TipoBloqueo convertirTipoBloqueo(TipoBloqueo tipo) {
        switch (tipo) {
            case HARD_BOUNCE:
                return com.acosux.MSCorreos.entidades.TipoBloqueo.HARD_BOUNCE;
            case SOFT_BOUNCE_REPETIDO:
                return com.acosux.MSCorreos.entidades.TipoBloqueo.SOFT_BOUNCE_REPETIDO;
            case COMPLAINT:
                return com.acosux.MSCorreos.entidades.TipoBloqueo.COMPLAINT;
            case MANUAL:
                return com.acosux.MSCorreos.entidades.TipoBloqueo.MANUAL;
            default:
                throw new IllegalArgumentException("Tipo de bloqueo no reconocido: " + tipo);
        }
    }
    
    /**
     * Convierte una entidad ListaNegra a DTO.
     * 
     * @param entidad Entidad a convertir
     * @return DTO con los datos de la entidad
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
