package com.acosux.MSCorreos.service;

import com.acosux.MSCorreos.entidades.ListaNegra;
import com.acosux.MSCorreos.entidades.ListaNegraHistorial;
import com.acosux.MSCorreos.entidades.TipoBloqueo;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de infraestructura para gestión centralizada de la lista negra de correos.
 *
 * <p>Este servicio es el punto único de acceso a todas las operaciones sobre
 * {@code correos.cor_lista_negra} y {@code correos.cor_lista_negra_historial}.
 * Implementa cache Caffeine con TTL de 5 minutos para las consultas de bloqueo
 * frecuentes, reduciendo la carga sobre la base de datos.</p>
 *
 * <p>Tanto {@code ProcesarEventoTrackingUseCaseImpl} como
 * {@code GestionarListaNegraUseCaseImpl} deben delegar en este servicio para
 * cualquier operación sobre la lista negra.</p>
 *
 * <p>Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6</p>
 *
 * @author MSCorreos Team
 */
public interface BlacklistService {

    /**
     * Verifica si un email está actualmente bloqueado en la lista negra.
     *
     * <p>El resultado se almacena en cache ({@code listaNegraCache}) con TTL de
     * 5 minutos. Para {@code SOFT_BOUNCE_REPETIDO} también verifica que el último
     * soft bounce haya ocurrido dentro de los últimos 30 días.</p>
     *
     * @param email Dirección de correo a verificar (se normaliza a minúsculas)
     * @return {@code true} si el email está bloqueado y activo; {@code false} en
     *         caso contrario o si el email tiene formato inválido
     * @see com.acosux.MSCorreos.entidades.ListaNegra#debeSerBloqueado()
     */
    boolean estaEnListaNegra(String email);

    /**
     * Agrega un email a la lista negra y registra la acción en el historial.
     *
     * <p>Si el email ya existe (activo o inactivo) actualiza {@code activo=true},
     * {@code tipo_bloqueo} y {@code motivo}. Si no existe crea un nuevo registro.
     * Siempre invalida la entrada de cache correspondiente.</p>
     *
     * @param email      Dirección de correo a bloquear
     * @param motivo     Descripción del motivo del bloqueo
     * @param tipo       Tipo de bloqueo a aplicar
     * @param usuario    Usuario que realiza la acción; {@code null} para acciones
     *                   automáticas del sistema
     * @throws IllegalArgumentException si el email tiene formato inválido
     */
    void agregarAListaNegra(String email, String motivo, TipoBloqueo tipo, String usuario);

    /**
     * Remueve un email de la lista negra (soft-delete) y registra la acción.
     *
     * <p>Marca {@code activo=false} en lugar de eliminar el registro, preservando
     * el historial para auditoría. Invalida la entrada de cache correspondiente.</p>
     *
     * @param email   Dirección de correo a desbloquear
     * @param motivo  Descripción del motivo de la remoción
     * @param usuario Usuario que realiza la acción
     * @throws IllegalArgumentException si el email no existe en la lista negra
     */
    void removerDeListaNegra(String email, String motivo, String usuario);

    /**
     * Incrementa el contador de soft bounces de un email y aplica bloqueo
     * automático si se alcanza el umbral de 3 bounces en 30 días.
     *
     * <p>Lógica:</p>
     * <ol>
     *   <li>Si el email no existe en la tabla, crea un registro con
     *       {@code activo=false} y {@code contador_soft_bounce=1}.</li>
     *   <li>Si el último soft bounce fue hace más de 30 días, resetea el
     *       contador a 0 antes de incrementar.</li>
     *   <li>Incrementa {@code contador_soft_bounce} y actualiza
     *       {@code ultimo_soft_bounce}.</li>
     *   <li>Si {@code contador_soft_bounce >= 3}, activa el bloqueo con
     *       {@code tipo_bloqueo=SOFT_BOUNCE_REPETIDO} y registra en historial.</li>
     * </ol>
     *
     * @param email      Dirección de correo que generó el soft bounce
     * @param bounceType Subtipo de bounce reportado por Amazon SES
     */
    void incrementarSoftBounce(String email, String bounceType);

    /**
     * Obtiene el registro de lista negra de un email (activo o inactivo).
     *
     * @param email Dirección de correo a buscar
     * @return {@code Optional} con el registro si existe
     */
    Optional<ListaNegra> obtenerRegistro(String email);

    /**
     * Obtiene el historial completo de cambios de un email, ordenado por fecha
     * descendente.
     *
     * @param email Dirección de correo a consultar
     * @return Lista de entradas de historial; vacía si no hay registros
     */
    List<ListaNegraHistorial> obtenerHistorial(String email);
}
