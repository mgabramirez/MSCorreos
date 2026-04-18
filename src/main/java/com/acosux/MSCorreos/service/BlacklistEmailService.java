package com.acosux.MSCorreos.service;

import com.acosux.MSCorreos.entidades.BlacklistEmail;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Servicio para gestionar la blacklist de correos electrónicos.
 * Este componente es GLOBAL, no está asociado a ninguna empresa.
 */
public interface BlacklistEmailService {
    
    /**
     * Verifica si un correo está bloqueado en la blacklist.
     * Usa patrón cache-aside: consulta cache primero, luego base de datos.
     * 
     * @param email Correo a verificar
     * @return true si el correo está bloqueado, false en caso contrario
     */
    boolean isEmailBloqueado(String email);
    
    /**
     * Agrega un correo a la blacklist.
     * 
     * @param email Correo a bloquear
     * @param razon Razón del bloqueo
     * @param usuario Usuario que realiza el bloqueo
     * @return El registro creado o actualizado
     */
    BlacklistEmail agregarEmail(String email, String razon, String usuario);
    
    /**
     * Elimina (desactiva) un correo de la blacklist.
     * 
     * @param email Correo a desbloquear
     * @param usuario Usuario que realiza la operación
     */
    void eliminarEmail(String email, String usuario);
    
    /**
     * Lista los correos en blacklist con paginación.
     * 
     * @param inicio Índice inicial
     * @param cantidad Cantidad de registros
     * @return Lista de correos bloqueados
     */
    List<BlacklistEmail> listarEmails(int inicio, int cantidad);
    
    /**
     * Lista los correos en blacklist con paginación usando Spring Pageable.
     * 
     * @param pageable Parámetros de paginación
     * @return Página de correos bloqueados
     */
    Page<BlacklistEmail> listarEmails(Pageable pageable);
    
    /**
     * Cuenta los correos activos en la blacklist.
     * 
     * @return Cantidad de correos bloqueados
     */
    long countEmails();
}