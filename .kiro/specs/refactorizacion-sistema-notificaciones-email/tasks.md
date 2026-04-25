# Plan de Implementación: Consolidación de Tablas de Notificaciones

## Overview

Este plan implementa la consolidación de 12+ tablas dispersas de notificaciones en una única tabla `correos.cor_notificaciones` dentro de MSCorreos. El sistema recibirá eventos de tracking desde Amazon SNS, gestionará una lista negra centralizada, y expondrá una API REST para consultas.

**Contexto**: ShrimpSoftServer continuará usando UtilsMail.java para enviar correos mediante Amazon SES (NO se modifica). MSCorreos SOLO recibe eventos de tracking SNS y gestiona la lista negra.

**Tecnologías**: Java 11+, Spring Boot 2.7+, PostgreSQL 12+, AWS SDK 2.x, JUnit 5, jqwik (property-based testing)

**Arquitectura**: Clean Architecture con capas Domain, Application, Infrastructure, Presentation

## Tasks

- [x] 1. Configurar base de datos y tablas
  - Crear esquema correos y tablas: cor_notificaciones, cor_lista_negra, cor_lista_negra_historial, cor_plantillas
  - Crear índices para optimización de consultas
  - Agregar constraints y validaciones
  - _Requirements: 2.1, 2.2, 2.6, 6.1, 6.7_

- [x] 2. Configurar infraestructura AWS (COMPLETADO)
  - Infraestructura AWS ya configurada: SES, SNS, S3, CloudWatch
  - Configuration Set de SES configurado para tracking
  - SNS Topic configurado para eventos de tracking
  - _Requirements: 5.1, 5.2_

- [ ] 3. Implementar capa de dominio (Domain Layer)
  - [x] 3.1 Crear entidades JPA
    - Implementar CorreosNotificaciones con anotaciones JPA
    - Implementar ListaNegra con método incrementarSoftBounce() y debeSerBloqueado()
    - Implementar ListaNegraHistorial
    - Implementar Plantilla (futuro)
    - _Requirements: 2.1, 2.2, 6.1, 6.7_

  - [x] 3.2 Crear Value Objects
    - Implementar EmailAddress con validación RFC 5322
    - Implementar ClaveAcceso con validación de 49 dígitos
    - _Requirements: 13.5_

  - [x] 3.3 Crear interfaces de repositorio
    - Definir NotificacionesRepository con métodos de consulta
    - Definir ListaNegraRepository con findByEmailAndActivoTrue()
    - Definir ListaNegraHistorialRepository
    - Definir PlantillaRepository con findByEmpresaAndTipoNotificacion()
    - _Requirements: 2.1, 6.1, 7.1, 7.2_

  - [x] 3.4 Crear enums del dominio
    - Crear TipoEvento (SEND, DELIVERY, OPEN, BOUNCE_TRANSIENT, BOUNCE_PERMANENT, COMPLAINT, BLOCKED)
    - Crear TipoBloqueo (HARD_BOUNCE, SOFT_BOUNCE_REPETIDO, COMPLAINT, MANUAL)
    - Crear AccionListaNegra (AGREGAR, REMOVER)
    - _Requirements: 5.3, 5.4, 5.7, 5.8, 6.2, 6.3, 6.4, 6.5_

- [ ] 4. Implementar capa de aplicación (Application Layer)
  - [x] 4.1 Crear DTOs
    - Implementar TrackingEventDTO y clases relacionadas (MailInfo, BounceInfo, ComplaintInfo, DeliveryInfo, OpenInfo)
    - Implementar NotificacionDTO y NotificacionDetalleDTO
    - Implementar ListaNegraDTO y AgregarListaNegraRequest
    - Implementar FiltrosNotificacion y FiltrosListaNegra
    - _Requirements: 5.9, 5.10, 7.4, 8.1, 8.3_

  - [x] 4.2 Definir interfaces de casos de uso
    - Definir ProcesarEventoTrackingUseCase
    - Definir ConsultarNotificacionesUseCase
    - Definir GestionarListaNegraUseCase
    - _Requirements: 5.1, 7.1, 8.1_

  - [x] 4.3 Implementar caso de uso: ProcesarEventoTrackingUseCase
    - Extraer metadatos de tags (empresa, ruc, clave, tipo_notificacion)
    - Registrar evento en cor_notificaciones según tipo (Send, Delivery, Open, Bounce, Complaint)
    - Para Bounce Permanent: agregar a lista negra automáticamente con tipo HARD_BOUNCE
    - Para Complaint: agregar a lista negra automáticamente con tipo COMPLAINT
    - Para Soft Bounce: incrementar contador y bloquear si >= 3 en 30 días
    - _Requirements: 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 6.2, 6.3, 6.4, 6.5_

  - [x] 4.4 Implementar caso de uso: ConsultarNotificacionesUseCase
    - Aplicar filtros (empresa, ruc, tipo, fechas, destinatario, tipo_notificacion)
    - Implementar paginación
    - Retornar DTOs con campos requeridos
    - Implementar obtenerDetalle() que incluye JSON completo
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 4.5 Implementar caso de uso: GestionarListaNegraUseCase
    - Implementar agregar() con registro en historial
    - Implementar remover() con registro en historial
    - Implementar estaEnListaNegra() con cache
    - Implementar consultar() con filtros y paginación
    - Implementar obtenerHistorial() para un email específico
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

- [ ] 5. Implementar capa de infraestructura (Infrastructure Layer)
  - [x] 5.1 Implementar BlacklistService
    - Implementar estaEnListaNegra() que consulta cor_lista_negra
    - Implementar cache con Caffeine (TTL 5 minutos)
    - Implementar agregarAListaNegra() con registro en historial
    - Implementar removerDeListaNegra() con registro en historial
    - Implementar incrementarSoftBounce() con lógica de 3+ bounces en 30 días
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 5.2 Implementar SNSMessageValidator
    - Validar firma de mensajes SNS usando certificado X.509
    - Validar que URL de certificado es de AWS
    - Construir string to sign según tipo de mensaje
    - Verificar firma con algoritmo SHA1withRSA
    - _Requirements: 13.2_

  - [ ] 5.3 Implementar CloudWatchMetricsService
    - Publicar métricas: EventosRecibidos, EventosProcesados, EventosFallidos, EmailsBloqueados
    - Configurar namespace "MSCorreos"
    - Agregar dimensiones: Empresa, TipoNotificacion, TipoEvento
    - _Requirements: 12.4, 12.5_

  - [ ] 5.4 Implementar StructuredLogger
    - Implementar logging estructurado con timestamp, nivel, mensaje, empresa, tipo_notificacion, destinatario
    - No registrar información sensible (claves completas, contraseñas)
    - Configurar niveles INFO, WARN, ERROR
    - _Requirements: 12.1, 12.2, 13.4_

  - [ ] 5.5 Implementar InputValidator
    - Validar formato de emails con regex RFC 5322
    - Detectar intentos de inyección SQL, XSS
    - Implementar sanitización de inputs
    - _Requirements: 13.3_

  - [ ] 5.6 Configurar connection pooling
    - Configurar HikariCP: max 20 conexiones, min 5 idle, timeout 30s
    - Configurar cache de prepared statements
    - _Requirements: NF1.2_

  - [ ] 5.7 Configurar Secrets Manager
    - Cargar credenciales de BD desde AWS Secrets Manager
    - Configurar AWS SDK clients con roles IAM
    - _Requirements: 13.1_

- [ ] 6. Implementar capa de presentación (Presentation Layer)
  - [x] 6.1 Implementar SNSListenerController
    - Crear endpoint POST /api/v1/sns/tracking
    - Validar firma SNS para seguridad
    - Manejar SubscriptionConfirmation automáticamente
    - Parsear eventos de tracking y delegar a ProcesarEventoTrackingUseCase
    - Retornar HTTP 200 OK para confirmación SNS
    - _Requirements: 5.1, 5.2, 13.2_

  - [x] 6.2 Implementar NotificacionesController
    - Crear endpoint GET /api/v1/notificaciones con filtros y paginación
    - Crear endpoint GET /api/v1/notificaciones/{id} para detalle completo
    - Crear endpoint GET /api/v1/notificaciones/estadisticas para métricas agregadas
    - Validar API Key en header X-API-Key
    - Retornar HTTP 401 si API Key inválida
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 13.1, 13.5_

  - [x] 6.3 Implementar ListaNegraController
    - Crear endpoint GET /api/v1/lista-negra con filtros y paginación
    - Crear endpoint POST /api/v1/lista-negra para agregar emails manualmente
    - Crear endpoint DELETE /api/v1/lista-negra/{email} para remover
    - Crear endpoint GET /api/v1/lista-negra/{email} para consultar estado
    - Crear endpoint GET /api/v1/lista-negra/{email}/historial para historial de cambios
    - Validar API Key
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 13.1, 13.5_

  - [x] 6.4 Implementar HealthController
    - Crear endpoint GET /health
    - Verificar conectividad a BD (isValid)
    - Retornar HTTP 200 si todo operativo, 503 si hay problemas
    - _Requirements: 12.3, NF2.2_

  - [x] 6.5 Implementar API Key authentication
    - Crear filtro para validar X-API-Key header
    - Configurar Spring Security para endpoints /api/v1/notificaciones/* y /api/v1/lista-negra/*
    - Permitir acceso sin autenticación a /health y /api/v1/sns/*
    - _Requirements: 13.1, 13.5_

  - [ ] 6.6 Implementar AuditAspect
    - Registrar todos los accesos a API con: timestamp, endpoint, parámetros
    - Usar @Around para interceptar llamadas a controllers
    - _Requirements: NF4.1, NF4.2_

- [ ] 7. Implementar configuración y wiring
  - [ ] 7.1 Crear application.yml
    - Configurar datasource (PostgreSQL)
    - Configurar AWS (region, SNS endpoint)
    - Configurar logging (CloudWatch log group)
    - Configurar cache (Caffeine)
    - Configurar API Key
    - _Requirements: NF5.1, NF5.2, NF5.3_

  - [ ] 7.2 Crear clases de configuración Spring
    - Crear CacheConfig con Caffeine
    - Crear SecurityConfig con API Key filter
    - Crear SecretsConfig para cargar credenciales
    - Crear AwsConfig para AWS SDK clients (CloudWatch)
    - _Requirements: 13.1_

  - [ ] 7.3 Configurar inyección de dependencias
    - Anotar servicios con @Service, @Component
    - Configurar @Autowired en constructores
    - Configurar @Bean para AWS clients (CloudWatchClient)
    - _Requirements: NF3.4_

  - [ ] 7.4 Crear Dockerfile
    - Usar imagen base Java 11
    - Copiar JAR de aplicación
    - Exponer puerto 8080
    - Configurar ENTRYPOINT
    - _Requirements: NF5.1_

  - [ ] 7.5 Crear docker-compose.yml para desarrollo local
    - Configurar PostgreSQL
    - Configurar LocalStack para simular AWS SNS
    - Configurar MSCorreos con variables de entorno
    - _Requirements: NF5.3_

- [ ] 8. Checkpoint - Validar integración completa
  - Verificar que aplicación inicia correctamente
  - Verificar que health check retorna 200
  - Verificar que puede conectarse a BD
  - Verificar que API Key authentication funciona
  - Verificar que endpoint SNS puede recibir eventos de prueba
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 9. Implementar tests unitarios
  - [ ]* 9.1 Tests para BlacklistService
    - Test: debeBloquearEmailConHardBounce
    - Test: debePermitirEmailNoEnListaNegra
    - Test: debeIncrementarContadorSoftBounce
    - Test: debeBloquearDespuesDe3SoftBouncesEn30Dias
    - Test: debeUsarCacheParaConsultas
    - _Requirements: 6.2, 6.3, 6.4, 6.5_

  - [ ]* 9.2 Tests para SNSMessageValidator
    - Test: debeValidarFirmaValida
    - Test: debeRechazarFirmaInvalida
    - Test: debeValidarURLCertificadoAWS
    - Test: debeRechazarURLCertificadoNoAWS
    - _Requirements: 13.2_

  - [ ]* 9.3 Tests para InputValidator
    - Test: debeValidarEmailsValidos
    - Test: debeRechazarEmailsInvalidos
    - Test: debeDetectarInyeccionSQL
    - Test: debeDetectarXSS
    - Test: debeSanitizarInputs
    - _Requirements: 13.3_

  - [ ]* 9.4 Tests para casos de uso
    - Test: ProcesarEventoTrackingUseCase debe registrar evento Send
    - Test: ProcesarEventoTrackingUseCase debe agregar a lista negra en Bounce Permanent
    - Test: ProcesarEventoTrackingUseCase debe incrementar contador en Soft Bounce
    - Test: ProcesarEventoTrackingUseCase debe agregar a lista negra en Complaint
    - Test: ConsultarNotificacionesUseCase debe aplicar filtros correctamente
    - Test: GestionarListaNegraUseCase debe registrar en historial
    - _Requirements: 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 6.2, 6.3, 6.6, 7.2, 8.6_

  - [ ]* 9.5 Tests para controllers
    - Test: SNSListenerController debe validar firma SNS
    - Test: SNSListenerController debe confirmar suscripción automáticamente
    - Test: NotificacionesController debe validar API Key
    - Test: NotificacionesController debe aplicar paginación
    - Test: ListaNegraController debe agregar email a lista negra
    - Test: HealthController debe verificar conectividad
    - _Requirements: 5.2, 7.3, 8.1, 12.3, 13.1, 13.2, 13.5_

- [ ] 10. Implementar property-based tests
  - [ ]* 10.1 Property test: Registro de eventos de tracking
    - **Property 1: Registro Completo de Eventos SNS**
    - **Validates: Requirements 5.3, 5.4, 5.5, 5.6, 5.7, 5.8**
    - Generar eventos SNS aleatorios y verificar que se registran en cor_notificaciones con todos los campos

  - [ ]* 10.2 Property test: Extracción de metadatos de tags
    - **Property 2: Preservación de Metadatos en Tags**
    - **Validates: Requirements 5.9, 5.10**
    - Verificar que tags (empresa, ruc, clave, tipo_notificacion) se extraen correctamente del evento SNS

  - [ ]* 10.3 Property test: Validación de firma SNS
    - **Property 3: Validación de Firma SNS**
    - **Validates: Requirements 13.2, 13.6**
    - Verificar que mensajes con firma inválida son rechazados

  - [ ]* 10.4 Property test: Agregado automático a lista negra
    - **Property 4: Agregado Automático por Hard Bounce**
    - **Validates: Requirements 6.2**
    - Verificar que Hard Bounce agrega email a lista negra automáticamente

  - [ ]* 10.5 Property test: Agregado automático por Complaint
    - **Property 5: Agregado Automático por Complaint**
    - **Validates: Requirements 6.3**
    - Verificar que Complaint agrega email a lista negra automáticamente

  - [ ]* 10.6 Property test: Bloqueo por soft bounces repetidos
    - **Property 6: Bloqueo por 3+ Soft Bounces en 30 Días**
    - **Validates: Requirements 6.4, 6.5**
    - Verificar que 3+ soft bounces en 30 días bloquean email automáticamente

  - [ ]* 10.7 Property test: Validación de formato de email
    - **Property 7: Validación de Formato de Email**
    - **Validates: Requirements 13.3**
    - Generar strings aleatorios y verificar que solo emails RFC 5322 son aceptados

  - [ ]* 10.8 Property test: Logging estructurado completo
    - **Property 8: Logging Estructurado Completo**
    - **Validates: Requirements 12.1, 12.2, 13.4**
    - Verificar que logs contienen campos requeridos y no información sensible

  - [ ]* 10.9 Property test: Publicación de métricas
    - **Property 9: Publicación de Métricas**
    - **Validates: Requirements 12.4, 12.5**
    - Verificar que métricas se publican para todos los eventos procesados

  - [ ]* 10.10 Property test: Filtros de API de notificaciones
    - **Property 10: Filtros de API de Notificaciones**
    - **Validates: Requirements 7.2**
    - Verificar que resultados cumplen con todos los filtros aplicados

  - [ ]* 10.11 Property test: Paginación de API
    - **Property 11: Paginación de API**
    - **Validates: Requirements 7.3**
    - Verificar que páginas no tienen duplicados ni omisiones

  - [ ]* 10.12 Property test: Estructura de respuesta API
    - **Property 12: Estructura de Respuesta API**
    - **Validates: Requirements 7.4**
    - Verificar que respuestas contienen todos los campos requeridos

  - [ ]* 10.13 Property test: Autenticación API con API Key
    - **Property 13: Autenticación API con API Key**
    - **Validates: Requirements 13.1, 13.5**
    - Verificar que requests sin API Key válida retornan 401

  - [ ]* 10.14 Property test: Validación y sanitización de inputs
    - **Property 14: Validación y Sanitización de Inputs**
    - **Validates: Requirements 13.3**
    - Verificar que inputs maliciosos son rechazados

  - [ ]* 10.15 Property test: Historial de cambios en lista negra
    - **Property 15: Historial de Cambios en Lista Negra**
    - **Validates: Requirements 8.6**
    - Verificar que todos los cambios se registran en historial

  - [ ]* 10.16 Property test: Cache de lista negra
    - **Property 16: Cache de Lista Negra**
    - **Validates: Requirements 6.6**
    - Verificar que cache reduce consultas a BD y se invalida correctamente

  - [ ]* 10.17 Property test: Preservación de datos en migración
    - **Property 17: Preservación de Datos en Migración**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4, 9.1, 9.2**
    - Verificar que datos migrados mantienen integridad

- [ ] 11. Implementar tests de integración
  - [ ]* 11.1 Test de integración: Flujo completo de recepción SNS
    - Simular evento SNS → SNS Listener procesa → Registra en BD → Actualiza lista negra
    - Usar Testcontainers para PostgreSQL
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 6.2, 6.3_

  - [ ]* 11.2 Test de integración: API REST de notificaciones
    - Consultar notificaciones con filtros → Verificar resultados
    - Obtener detalle de notificación → Verificar JSON completo
    - Usar Testcontainers para PostgreSQL
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 11.3 Test de integración: API REST de lista negra
    - Agregar/remover de lista negra → Verificar historial
    - Consultar lista negra con filtros → Verificar resultados
    - Usar Testcontainers para PostgreSQL
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

- [ ] 12. Checkpoint - Validar cobertura de tests
  - Ejecutar JaCoCo para verificar cobertura >= 80%
  - Verificar que todos los property tests pasan con 100 iteraciones
  - Verificar que tests de integración pasan con Testcontainers
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 13. Implementar migración de datos
  - [ ] 13.1 Crear script SQL de migración
    - Consolidar datos de tablas antiguas (anx_venta_electronica_notificaciones, anx_compra_electronica_notificaciones, anx_guia_remision_electronica_notificaciones, etc.)
    - Mapear campos específicos a n_clave (periodo_motivo_numero, sector_motivo_numero, contable, cli_codigo)
    - Agregar flag "MIGRADO_DE: tabla_origen" en n_observacion
    - Resolver conflictos de n_secuencial
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10, 4.11_

  - [ ] 13.2 Crear script SQL para poblar lista negra inicial
    - Identificar correos con múltiples bounces permanentes en datos históricos
    - Identificar correos con complaints en datos históricos
    - Identificar correos con 3+ soft bounces en 30 días
    - Insertar en cor_lista_negra con tipo_bloqueo apropiado
    - _Requirements: 6.2, 6.3, 6.4_

  - [ ] 13.3 Ejecutar migración en ambiente de pruebas
    - Ejecutar scripts en transacción
    - Validar integridad: contar registros origen vs destino
    - Generar reporte de migración
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ] 13.4 Validar datos migrados
    - Verificar que todos los registros tienen campos requeridos
    - Verificar que n_observacion contiene flag de migración
    - Verificar que lista negra contiene correos problemáticos
    - Ejecutar queries de validación
    - _Requirements: 9.1, 9.2_

- [ ] 14. Desplegar en ambiente de pruebas
  - [ ] 14.1 Construir imagen Docker
    - Ejecutar mvn clean package
    - Construir imagen con Dockerfile
    - Subir a ECR (Elastic Container Registry)
    - _Requirements: NF5.1_

  - [ ] 14.2 Crear Task Definition en ECS
    - Configurar CPU 512, Memory 1024
    - Configurar variables de entorno
    - Configurar secrets desde Secrets Manager
    - Configurar health check
    - _Requirements: NF2.2_

  - [ ] 14.3 Crear Service en ECS
    - Configurar desiredCount = 2 (mínimo)
    - Configurar load balancer
    - Configurar endpoint público para SNS
    - _Requirements: NF2.4_

  - [ ] 14.4 Configurar suscripción SNS
    - Suscribir endpoint público de MSCorreos al topic SNS
    - Verificar que MSCorreos confirma suscripción automáticamente
    - _Requirements: 5.1, 5.2_

  - [ ] 14.5 Configurar monitoreo
    - Verificar que logs aparecen en CloudWatch
    - Verificar que métricas se publican
    - Verificar que alarmas están activas
    - _Requirements: 12.1, 12.4, 12.6, 12.7_

  - [ ] 14.6 Ejecutar pruebas end-to-end
    - Simular evento SNS de prueba
    - Verificar que evento se registra en BD
    - Simular bounce y verificar que se agrega a lista negra
    - Consultar API REST y verificar respuestas
    - _Requirements: 5.3, 6.2, 7.1, 8.1_

- [ ] 15. Ejecutar migración de datos en producción
  - [ ] 15.1 Backup de base de datos
    - Crear snapshot de PostgreSQL
    - Verificar que backup es restaurable
    - _Requirements: 9.5_

  - [ ] 15.2 Ejecutar scripts de migración
    - Ejecutar script de migración de datos históricos
    - Ejecutar script de población de lista negra inicial
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 6.2, 6.3, 6.4_

  - [ ] 15.3 Validar migración
    - Ejecutar queries de validación
    - Verificar conteos origen vs destino
    - Generar reporte de migración
    - _Requirements: 9.1, 9.2_

  - [ ] 15.4 Crear vistas de compatibilidad
    - Crear vistas SQL que emulen tablas dispersas
    - Verificar que consultas existentes funcionan con vistas
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7_

- [ ] 16. Desplegar en producción
  - [ ] 16.1 Desplegar MSCorreos en producción
    - Subir imagen Docker a ECR producción
    - Crear Task Definition y Service en ECS producción
    - Configurar load balancer con SSL/TLS
    - _Requirements: NF2.1_

  - [ ] 16.2 Configurar suscripción SNS en producción
    - Suscribir endpoint de producción al topic SNS
    - Verificar confirmación automática
    - _Requirements: 5.1, 5.2_

  - [ ] 16.3 Monitorear sistema durante 2 semanas
    - Verificar que eventos SNS se registran correctamente
    - Verificar que lista negra se actualiza automáticamente
    - Verificar que API REST funciona correctamente
    - Revisar logs diariamente para detectar problemas
    - _Requirements: 5.3, 6.2, 6.3, 7.1, 8.1, 12.1_

  - [ ] 16.4 Validar performance
    - Verificar que tiempo de procesamiento de eventos < 200ms (p95)
    - Verificar que latencia de API < 500ms (p95)
    - _Requirements: NF1.1, NF1.3_

  - [ ] 16.5 Validar disponibilidad
    - Verificar uptime >= 99.5%
    - Verificar que health checks funcionan
    - _Requirements: NF2.1, NF2.2_

- [ ] 17. Configurar tablas antiguas en modo solo lectura
  - [ ] 17.1 Revocar permisos de escritura
    - Revocar permisos de INSERT/UPDATE/DELETE en tablas dispersas
    - Mantener permisos de SELECT por 90 días
    - Documentar fecha de desactivación
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7_

  - [ ] 17.2 Monitorear durante 1 semana adicional
    - Verificar que no hay errores por falta de escritura en tablas antiguas
    - Verificar que sistema funciona correctamente
    - _Requirements: 2.1_

- [ ] 18. Limpieza y documentación
  - [ ] 18.1 Archivar tablas antiguas
    - Exportar datos a archivos CSV
    - Crear backup final
    - Programar eliminación de tablas después de 90 días
    - _Requirements: 10.7_

  - [ ] 18.2 Documentar arquitectura
    - Crear diagrama de arquitectura actualizado
    - Documentar flujos de proceso
    - Documentar configuración de AWS
    - Documentar API REST (OpenAPI/Swagger)
    - _Requirements: NF3.5_

  - [ ] 18.3 Crear runbooks operacionales
    - Procedimiento para agregar/remover emails de lista negra
    - Procedimiento para troubleshooting de errores comunes
    - Procedimiento para consultar notificaciones
    - _Requirements: NF3.1_

  - [ ] 18.4 Capacitar al equipo
    - Sesión de capacitación sobre nueva arquitectura
    - Sesión sobre uso de API REST
    - Sesión sobre monitoreo y troubleshooting
    - Entregar documentación y runbooks
    - _Requirements: NF3.1_

  - [ ] 18.5 Establecer procedimientos de mantenimiento
    - Definir proceso de revisión de lista negra
    - Definir proceso de análisis de métricas
    - Definir proceso de respuesta a alarmas
    - _Requirements: NF3.1, NF4.1_

- [ ] 19. Checkpoint final - Validar proyecto completo
  - Verificar que todos los requisitos funcionales están implementados
  - Verificar que todos los requisitos no funcionales se cumplen
  - Verificar que lista negra se actualiza automáticamente
  - Verificar que no hay pérdida de datos históricos
  - Verificar que API REST funciona correctamente
  - Verificar que equipo está capacitado
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Las tareas marcadas con `*` son opcionales (tests) y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Property tests validan propiedades universales de correctness
- Unit tests validan casos específicos y edge cases
- La migración se realiza de forma gradual para minimizar riesgo
- El sistema implementa Clean Architecture para facilitar mantenimiento
- La lista negra se actualiza automáticamente basándose en eventos SNS
- El monitoreo es esencial: logs, métricas y alarmas deben configurarse desde el inicio
- ShrimpSoftServer NO se modifica: continúa usando UtilsMail.java para enviar correos
- MSCorreos SOLO recibe eventos de tracking SNS y gestiona lista negra centralizada

## Criterios de Éxito

1. ✅ Todas las notificaciones se almacenan en correos.cor_notificaciones
2. ✅ MSCorreos recibe eventos de tracking desde Amazon SNS
3. ✅ Eventos de tracking se registran automáticamente en tabla unificada
4. ✅ Lista negra se actualiza automáticamente (Hard Bounce, Complaint, Soft Bounce repetidos)
5. ✅ API REST permite consultar notificaciones con filtros y paginación
6. ✅ API REST permite gestionar lista negra (agregar, remover, consultar, historial)
7. ✅ Datos históricos migrados sin pérdida de información
8. ✅ Vistas SQL de compatibilidad permiten consultar datos usando nombres de tablas antiguas
9. ✅ Performance y disponibilidad cumplen requisitos no funcionales
10. ✅ Observabilidad completa con CloudWatch (logs, métricas, alarmas)
11. ✅ Sistema está en producción y operando correctamente durante al menos 30 días
12. ✅ Equipo está capacitado y documentación está completa
