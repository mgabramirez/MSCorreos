package com.acosux.MSCorreos.service;

import com.acosux.MSCorreos.entidades.AccionListaNegra;
import com.acosux.MSCorreos.entidades.ListaNegra;
import com.acosux.MSCorreos.entidades.ListaNegraHistorial;
import com.acosux.MSCorreos.entidades.TipoBloqueo;
import com.acosux.MSCorreos.repositories.ListaNegraHistorialRepository;
import com.acosux.MSCorreos.repositories.ListaNegraRepository;
import com.acosux.MSCorreos.valueobjects.EmailAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Implementación del servicio de lista negra de correos.
 *
 * <p>Centraliza todas las operaciones sobre {@code correos.cor_lista_negra} y
 * {@code correos.cor_lista_negra_historial}. Usa Caffeine cache con TTL de
 * 5 minutos para las consultas de bloqueo, reduciendo la carga sobre la BD.</p>
 *
 * <p>Reglas de negocio implementadas:</p>
 * <ul>
 *   <li>Hard Bounce → bloqueo inmediato con {@code HARD_BOUNCE}</li>
 *   <li>Complaint → bloqueo inmediato con {@code COMPLAINT}</li>
 *   <li>Soft Bounce × 3 en 30 días → bloqueo con {@code SOFT_BOUNCE_REPETIDO}</li>
 *   <li>Soft Bounce con último bounce > 30 días → resetear contador</li>
 * </ul>
 *
 * <p>Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6</p>
 *
 * @author MSCorreos Team
 */
@Service
@Transactional
public class BlacklistServiceImpl implements BlacklistService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistServiceImpl.class);

    /** Ventana de tiempo en días para contar soft bounces. */
    private static final int SOFT_BOUNCE_VENTANA_DIAS = 30;

    private final ListaNegraRepository listaNegraRepository;
    private final ListaNegraHistorialRepository listaNegraHistorialRepository;

    @Autowired
    public BlacklistServiceImpl(
            ListaNegraRepository listaNegraRepository,
            ListaNegraHistorialRepository listaNegraHistorialRepository) {
        this.listaNegraRepository = listaNegraRepository;
        this.listaNegraHistorialRepository = listaNegraHistorialRepository;
    }

    // -------------------------------------------------------------------------
    // estaEnListaNegra
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>El resultado se almacena en {@code listaNegraCache} con TTL de 5 minutos
     * (configurado en {@link com.acosux.MSCorreos.config.CacheConfig}).
     * Para {@code SOFT_BOUNCE_REPETIDO} delega en
     * {@link ListaNegra#debeSerBloqueado()} que verifica la ventana de 30 días.</p>
     */
    @Override
    @Cacheable(value = "listaNegraCache", key = "#email")
    @Transactional(readOnly = true)
    public boolean estaEnListaNegra(String email) {
        log.debug("Verificando lista negra (cache miss): email={}", email);

        try {
            String emailNorm = normalizar(email);
            Optional<ListaNegra> registro = listaNegraRepository.findByEmailAndActivoTrue(emailNorm);

            if (!registro.isPresent()) {
                log.debug("Email no está en lista negra: {}", emailNorm);
                return false;
            }

            boolean bloqueado = registro.get().debeSerBloqueado();
            log.debug("Email en lista negra: email={}, bloqueado={}, tipo={}",
                    emailNorm, bloqueado, registro.get().getTipoBloqueo());
            return bloqueado;

        } catch (IllegalArgumentException e) {
            // Email con formato inválido → no está bloqueado
            log.debug("Formato de email inválido al verificar lista negra: {}", email);
            return false;
        } catch (Exception e) {
            log.error("Error verificando email en lista negra: {}", email, e);
            // Ante error de infraestructura, no bloquear para no interrumpir envíos
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // agregarAListaNegra
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Si el email ya existe (activo o inactivo) actualiza el registro en lugar
     * de crear uno duplicado. Siempre invalida la entrada de cache.</p>
     */
    @Override
    @CacheEvict(value = "listaNegraCache", key = "#email")
    public void agregarAListaNegra(String email, String motivo, TipoBloqueo tipo, String usuario) {
        log.info("Agregando email a lista negra: email={}, tipo={}, usuario={}", email, tipo, usuario);

        try {
            String emailNorm = normalizar(email);

            Optional<ListaNegra> existente = listaNegraRepository.findByEmail(emailNorm);
            ListaNegra registro;

            if (existente.isPresent()) {
                registro = existente.get();
                registro.setActivo(true);
                registro.setTipoBloqueo(tipo);
                registro.setMotivo(motivo);
                log.debug("Actualizando registro existente en lista negra: id={}", registro.getId());
            } else {
                registro = new ListaNegra();
                registro.setEmail(emailNorm);
                registro.setMotivo(motivo);
                registro.setTipoBloqueo(tipo);
                registro.setActivo(true);
                registro.setFechaRegistro(new Date());
                registro.setContadorSoftBounce(0);
                log.debug("Creando nuevo registro en lista negra: email={}", emailNorm);
            }

            listaNegraRepository.save(registro);
            registrarHistorial(emailNorm, AccionListaNegra.AGREGAR, motivo, usuario);

            log.info("Email agregado a lista negra: email={}, id={}, tipo={}",
                    emailNorm, registro.getId(), tipo);

        } catch (IllegalArgumentException e) {
            log.error("Email inválido al agregar a lista negra: {}", email, e);
            throw e;
        } catch (Exception e) {
            log.error("Error agregando email a lista negra: {}", email, e);
            throw new RuntimeException("Error agregando email a lista negra: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // removerDeListaNegra
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Implementa soft-delete: marca {@code activo=false} preservando el
     * registro para auditoría. Invalida la entrada de cache.</p>
     */
    @Override
    @CacheEvict(value = "listaNegraCache", key = "#email")
    public void removerDeListaNegra(String email, String motivo, String usuario) {
        log.info("Removiendo email de lista negra: email={}, usuario={}", email, usuario);

        try {
            String emailNorm = normalizar(email);

            Optional<ListaNegra> existente = listaNegraRepository.findByEmail(emailNorm);
            if (!existente.isPresent()) {
                String msg = "Email no encontrado en lista negra: " + emailNorm;
                log.warn(msg);
                throw new IllegalArgumentException(msg);
            }

            ListaNegra registro = existente.get();
            registro.setActivo(false);
            listaNegraRepository.save(registro);

            registrarHistorial(emailNorm, AccionListaNegra.REMOVER, motivo, usuario);

            log.info("Email removido de lista negra: email={}, id={}, usuario={}",
                    emailNorm, registro.getId(), usuario);

        } catch (IllegalArgumentException e) {
            log.error("Error al remover email de lista negra: {}", email, e);
            throw e;
        } catch (Exception e) {
            log.error("Error removiendo email de lista negra: {}", email, e);
            throw new RuntimeException("Error removiendo email de lista negra: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // incrementarSoftBounce
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Flujo detallado:</p>
     * <ol>
     *   <li>Busca el registro del email (activo o inactivo).</li>
     *   <li>Si no existe, crea uno con {@code activo=false} y contador=1.</li>
     *   <li>Si el último soft bounce fue hace más de {@value #SOFT_BOUNCE_VENTANA_DIAS}
     *       días, resetea el contador a 0 antes de incrementar.</li>
     *   <li>Llama a {@link ListaNegra#incrementarSoftBounce()} que incrementa el
     *       contador y activa el bloqueo si alcanza {@value #SOFT_BOUNCE_UMBRAL}.</li>
     *   <li>Si se activó el bloqueo, registra en historial e invalida cache.</li>
     * </ol>
     */
    @Override
    public void incrementarSoftBounce(String email, String bounceType) {
        log.info("Incrementando soft bounce: email={}, bounceType={}", email, bounceType);

        try {
            Optional<ListaNegra> existente = listaNegraRepository.findByEmail(email);
            ListaNegra registro;

            if (existente.isPresent()) {
                registro = existente.get();

                // Resetear contador si el último bounce fue hace más de 30 días
                if (debeResetearContador(registro)) {
                    log.info("Reseteando contador de soft bounces (ventana expirada): email={}", email);
                    registro.setContadorSoftBounce(0);
                }

                registro.incrementarSoftBounce();
                log.info("Contador de soft bounces incrementado: email={}, contador={}",
                        email, registro.getContadorSoftBounce());

            } else {
                // Primer soft bounce: crear registro sin bloquear aún
                registro = new ListaNegra();
                registro.setEmail(email);
                registro.setMotivo(String.format("Soft Bounce: %s", bounceType));
                registro.setTipoBloqueo(TipoBloqueo.SOFT_BOUNCE_REPETIDO); // se usará si alcanza umbral
                registro.setActivo(false);
                registro.setFechaRegistro(new Date());
                registro.setContadorSoftBounce(1);
                registro.setUltimoSoftBounce(new Date());
                log.info("Primer soft bounce registrado: email={}", email);
            }

            listaNegraRepository.save(registro);

            // Si se activó el bloqueo automático, registrar en historial e invalidar cache
            if (Boolean.TRUE.equals(registro.getActivo())
                    && TipoBloqueo.SOFT_BOUNCE_REPETIDO.equals(registro.getTipoBloqueo())) {
                String motivo = String.format(
                        "Bloqueado automáticamente por %d soft bounces en %d días",
                        registro.getContadorSoftBounce(), SOFT_BOUNCE_VENTANA_DIAS);

                registrarHistorial(email, AccionListaNegra.AGREGAR, motivo, null);
                invalidarCache(email);

                log.warn("Email bloqueado por soft bounces repetidos: email={}, contador={}",
                        email, registro.getContadorSoftBounce());
            }

        } catch (Exception e) {
            log.error("Error incrementando soft bounce: email={}", email, e);
            // No propagar: no debe interrumpir el procesamiento del evento SNS
        }
    }

    // -------------------------------------------------------------------------
    // obtenerRegistro / obtenerHistorial
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<ListaNegra> obtenerRegistro(String email) {
        try {
            String emailNorm = normalizar(email);
            return listaNegraRepository.findByEmail(emailNorm);
        } catch (IllegalArgumentException e) {
            log.debug("Email inválido al obtener registro: {}", email);
            return Optional.empty();
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<ListaNegraHistorial> obtenerHistorial(String email) {
        log.debug("Obteniendo historial de lista negra: email={}", email);
        try {
            String emailNorm = normalizar(email);
            List<ListaNegraHistorial> historial =
                    listaNegraHistorialRepository.findByEmailOrderByFechaDesc(emailNorm);
            log.debug("Historial obtenido: email={}, registros={}", emailNorm, historial.size());
            return historial;
        } catch (IllegalArgumentException e) {
            log.warn("Email inválido al obtener historial: {}", email);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error obteniendo historial: email={}", email, e);
            throw new RuntimeException("Error obteniendo historial: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    /**
     * Normaliza el email usando {@link EmailAddress} (lowercase + trim).
     *
     * @param email Email a normalizar
     * @return Email normalizado
     * @throws IllegalArgumentException si el formato es inválido
     */
    private String normalizar(String email) {
        return new EmailAddress(email).getValue();
    }

    /**
     * Determina si el contador de soft bounces debe resetearse porque la ventana
     * de {@value #SOFT_BOUNCE_VENTANA_DIAS} días ha expirado.
     *
     * @param registro Registro de lista negra
     * @return {@code true} si el último soft bounce fue hace más de 30 días
     */
    private boolean debeResetearContador(ListaNegra registro) {
        if (registro.getUltimoSoftBounce() == null) {
            return false;
        }
        Calendar limite = Calendar.getInstance();
        limite.add(Calendar.DAY_OF_MONTH, -SOFT_BOUNCE_VENTANA_DIAS);
        return registro.getUltimoSoftBounce().before(limite.getTime());
    }

    /**
     * Registra una acción en {@code cor_lista_negra_historial}.
     * Los errores se loguean pero no se propagan para no interrumpir el flujo principal.
     *
     * @param email   Email afectado
     * @param accion  Acción realizada
     * @param motivo  Motivo de la acción
     * @param usuario Usuario que realizó la acción; {@code null} para acciones automáticas
     */
    private void registrarHistorial(String email, AccionListaNegra accion, String motivo, String usuario) {
        try {
            ListaNegraHistorial historial = new ListaNegraHistorial(email, accion, motivo, usuario);
            listaNegraHistorialRepository.save(historial);
            log.debug("Historial registrado: email={}, accion={}, usuario={}", email, accion, usuario);
        } catch (Exception e) {
            log.error("Error registrando historial: email={}, accion={}", email, accion, e);
        }
    }

    /**
     * Invalida la entrada de cache para un email específico.
     * Se usa cuando el bloqueo se activa dentro de {@link #incrementarSoftBounce}
     * (que no tiene {@code @CacheEvict} porque no siempre activa el bloqueo).
     *
     * @param email Email cuya entrada de cache debe invalidarse
     */
    @CacheEvict(value = "listaNegraCache", key = "#email")
    public void invalidarCache(String email) {
        log.debug("Cache invalidado para email: {}", email);
    }
}
