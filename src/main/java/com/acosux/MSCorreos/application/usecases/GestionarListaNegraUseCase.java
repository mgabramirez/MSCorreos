package com.acosux.MSCorreos.application.usecases;

import com.acosux.MSCorreos.dtos.FiltrosListaNegra;
import com.acosux.MSCorreos.dtos.ListaNegraDTO;
import com.acosux.MSCorreos.entidades.ListaNegraHistorial;
import com.acosux.MSCorreos.enums.TipoBloqueo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Caso de uso para gestionar la lista negra de correos bloqueados.
 * 
 * Este caso de uso es responsable de:
 * - Implementar agregar() con registro en historial
 * - Implementar remover() con registro en historial
 * - Implementar estaEnListaNegra() con cache
 * - Implementar consultar() con filtros y paginación
 * - Implementar obtenerHistorial() para un email específico
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6
 */
public interface GestionarListaNegraUseCase {
    
    /**
     * Agrega un email a la lista negra con registro en historial.
     * 
     * @param email Email a bloquear
     * @param motivo Motivo del bloqueo
     * @param tipo Tipo de bloqueo (HARD_BOUNCE, SOFT_BOUNCE_REPETIDO, COMPLAINT, MANUAL)
     */
    void agregar(String email, String motivo, TipoBloqueo tipo);
    
    /**
     * Remueve un email de la lista negra (desactiva) con registro en historial.
     * 
     * @param email Email a desbloquear
     * @param motivo Motivo de la remoción
     * @param usuario Usuario que realiza la acción (NULL para acciones automáticas)
     */
    void remover(String email, String motivo, String usuario);
    
    /**
     * Verifica si un email está en lista negra (con cache).
     * 
     * @param email Email a verificar
     * @return true si está bloqueado y activo
     */
    boolean estaEnListaNegra(String email);
    
    /**
     * Consulta lista negra con filtros y paginación.
     * 
     * @param filtros Filtros de búsqueda (tipo_bloqueo, activo, fecha_desde, fecha_hasta)
     * @param pageable Configuración de paginación
     * @return Página de emails bloqueados que cumplen con los filtros
     */
    Page<ListaNegraDTO> consultar(FiltrosListaNegra filtros, Pageable pageable);
    
    /**
     * Obtiene historial de cambios de un email específico.
     * 
     * @param email Email a consultar
     * @return Lista de cambios históricos ordenados por fecha descendente
     */
    List<ListaNegraHistorial> obtenerHistorial(String email);
}
