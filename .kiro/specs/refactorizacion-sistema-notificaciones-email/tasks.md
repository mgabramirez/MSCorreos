# Plan de Implementación: Refactorización del Sistema de Notificaciones por Correo Electrónico

## Overview

Este plan de implementación desglosa la refactorización del sistema de notificaciones de MSCorreos en tareas ejecutables. El sistema implementará una arquitectura basada en eventos usando AWS (SQS, SES, SNS, CloudWatch, S3), centralizará notificaciones en una tabla única, y agregará un sistema de lista negra para prevenir envíos a correos problemáticos.

**Tecnologías**: Java 11+, Spring Boot 2.7+, PostgreSQL 12+, AWS SDK 2.x, Resilience4j, JUnit 5, jqwik (property-based testing)

**Arquitectura**: Clean Architecture con capas Domain, Application, Infrastructure, Presentation

## Tasks

- [x] 1. Configurar base de datos y tablas
  - Crear esquema correos y tablas: cor_notificaciones, cor_lista_negra, cor_lista_negra_historial, cor_plantillas
  - Crear índices para optimización de consultas
  - Agregar constraints y validaciones
  - _Requirements: 1.1, 1.3, 16.1, 12.1_

- [ ] 2. Configurar infraestructura AWS
  - [x] 2.1 Configurar colas SQS (Standard, FIFO, DLQ)
    - Crear cola Standard para prioridad media/baja
    - Crear cola FIFO para prioridad alta
    - Crear Dead Letter Queue
    - Configurar políticas de reintento y visibilidad
    - _Requirements: 2.1, 2.4, 2.5, 6.2, 6.7_

  - [x] 2.2 Configurar Amazon SES
    - Crear Configuration Set para tracking
    - Configurar Event Destination hacia SNS
    - Verificar identidades (dominios y emails)
    - Configurar límites de tasa (14 correos/segundo)
    - _Requirements: 3.2, 3.4, 4.1_


  - [x] 2.3 Configurar Amazon SNS
    - Crear topic para eventos de tracking
    - Configurar suscripción HTTP/HTTPS hacia MSCorreos
    - Configurar política de acceso para SES
    - _Requirements: 4.1, 4.2_

  - [x] 2.4 Configurar Amazon S3
    - Crear bucket para adjuntos
    - Configurar política de ciclo de vida (eliminar después de 7 días)
    - Configurar políticas de acceso para productores y consumidores
    - _Requirements: 13.1, 13.5_

  - [x] 2.5 Configurar CloudWatch
    - Crear log groups con retención de 30 días
    - Configurar alarmas: DLQ con >10 mensajes, tasa de errores >5%, tiempo procesamiento >5s, lista negra >1000 emails
    - _Requirements: 8.1, 8.3, 8.4, 8.5, 8.7, 16.15_

  - [ ] 2.6 Configurar roles y políticas IAM
    - Crear role para ECS Task (MSCorreos) con permisos SQS, SES, S3, CloudWatch, Secrets Manager
    - Crear role para productores (ShrimpSoftServer) con permisos SQS, S3
    - _Requirements: 9.2_

- [ ] 3. Checkpoint - Validar infraestructura AWS
  - Verificar que todas las colas SQS están creadas y configuradas
  - Verificar que SES puede enviar correos de prueba
  - Verificar que SNS puede publicar a endpoint de prueba
  - Verificar que S3 bucket acepta uploads
  - Verificar que alarmas CloudWatch están activas
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Implementar capa de dominio (Domain Layer)
  - [ ] 4.1 Crear entidades JPA
    - Implementar CorreosNotificaciones con anotaciones JPA
    - Implementar ListaNegra con método incrementarSoftBounce() y debeSerBloqueado()
    - Implementar ListaNegraHistorial
    - Implementar Plantilla
    - _Requirements: 1.3, 16.1, 12.1_


  - [ ] 4.2 Crear Value Objects
    - Implementar EmailAddress con validación RFC 5322
    - Implementar ClaveAcceso con validación de 49 dígitos
    - _Requirements: 3.7, 10.4_

  - [ ] 4.3 Crear interfaces de repositorio
    - Definir NotificacionesRepository con métodos de consulta
    - Definir ListaNegraRepository con findByEmailAndActivoTrue()
    - Definir ListaNegraHistorialRepository
    - Definir PlantillaRepository con findByEmpresaAndTipoNotificacion()
    - _Requirements: 1.1, 16.1, 12.1_

  - [ ] 4.4 Crear enums del dominio
    - Crear TipoBloqueo (HARD_BOUNCE, SOFT_BOUNCE_REPETIDO, COMPLAINT, MANUAL)
    - Crear AccionListaNegra (AGREGAR, REMOVER)
    - Crear PrioridadNotificacion (ALTA, MEDIA, BAJA)
    - _Requirements: 16.7, 15.1_

- [ ] 5. Implementar capa de aplicación (Application Layer)
  - [ ] 5.1 Crear DTOs
    - Implementar EventoNotificacion con todos los campos requeridos
    - Implementar AdjuntoReferencia
    - Implementar TrackingEvent y clases relacionadas (MailInfo, BounceInfo, ComplaintInfo)
    - Implementar NotificacionDTO y NotificacionDetalleDTO
    - Implementar ListaNegraDTO y AgregarListaNegraRequest
    - _Requirements: 2.2, 4.1, 11.4, 16.10_

  - [ ] 5.2 Definir interfaces de casos de uso
    - Definir EnviarNotificacionUseCase
    - Definir ProcesarTrackingEventUseCase
    - Definir ConsultarNotificacionesUseCase
    - Definir GestionarListaNegraUseCase
    - _Requirements: 3.1, 4.1, 11.1, 16.9_

  - [ ] 5.3 Implementar caso de uso: EnviarNotificacionUseCase
    - Validar estructura del evento
    - Coordinar validación de lista negra, carga de plantilla, construcción de mensaje, envío SES
    - Registrar evento en BD
    - Manejar errores y publicar métricas
    - _Requirements: 3.1, 3.2, 16.2_


  - [ ] 5.4 Implementar caso de uso: ProcesarTrackingEventUseCase
    - Extraer metadatos de tags
    - Registrar evento en cor_notificaciones según tipo (Send, Delivery, Open, Bounce, Complaint)
    - Para Bounce Permanent y Complaint: agregar a lista negra automáticamente
    - Para Soft Bounce: incrementar contador y bloquear si >= 3 en 30 días
    - _Requirements: 4.3, 4.4, 4.5, 4.6, 4.7, 16.5, 16.6, 16.8_

  - [ ] 5.5 Implementar caso de uso: ConsultarNotificacionesUseCase
    - Aplicar filtros (empresa, ruc, tipo, fechas, destinatario)
    - Implementar paginación
    - Retornar DTOs con campos requeridos
    - _Requirements: 11.2, 11.3, 11.4_

  - [ ] 5.6 Implementar caso de uso: GestionarListaNegraUseCase
    - Implementar agregar() con registro en historial
    - Implementar remover() con registro en historial
    - Implementar estaEnListaNegra() con cache
    - Implementar consultar() con filtros y paginación
    - _Requirements: 16.9, 16.10, 16.11, 16.12, 16.16_

- [ ] 6. Implementar capa de infraestructura (Infrastructure Layer)
  - [ ] 6.1 Implementar BlacklistService
    - Implementar validarDestinatarios() que consulta cor_lista_negra ANTES de cualquier envío
    - Implementar cache con Caffeine (TTL 5 minutos)
    - Registrar eventos "Blocked" para destinatarios en lista negra
    - Filtrar destinatarios bloqueados de listas múltiples
    - _Requirements: 16.2, 16.3, 16.4, 16.13_

  - [ ] 6.2 Implementar TemplateService
    - Implementar cargar plantilla por (empresa, tipoNotificacion)
    - Implementar sustitución de variables {{variable}}
    - Usar plantilla por defecto si no existe personalizada
    - Validar sintaxis de plantillas
    - _Requirements: 12.3, 12.4, 12.5, 12.6_


  - [ ] 6.3 Implementar EmailService
    - Validar formato de emails con EmailAddress value object
    - Construir mensaje MIME con HTML, texto plano y adjuntos
    - Soportar múltiples destinatarios
    - Validar tamaño total de adjuntos <= 10 MB
    - _Requirements: 3.6, 3.7, 3.8, 13.6_

  - [ ] 6.4 Implementar SESAdapter
    - Configurar SesClient con región us-east-1
    - Implementar enviar() con tags (ows-tipo-notificacion, ows-empresa, ows-ruc, ows-clave)
    - Configurar Circuit Breaker (Resilience4j): 50% failure rate, 10 calls, 60s wait
    - Configurar Retry con backoff exponencial: 3 intentos, 1s/2s/4s + jitter
    - Manejar errores temporales (throttling) vs permanentes (email inválido)
    - _Requirements: 3.2, 3.3, 6.3, 6.4, 6.6, 14.1, 14.2_

  - [ ] 6.5 Implementar S3Adapter
    - Implementar descargarAdjunto() desde S3
    - Implementar eliminarAdjunto() después de envío exitoso
    - Manejar errores de descarga (enviar sin adjunto si falla)
    - _Requirements: 13.3, 13.4, 13.7_

  - [ ] 6.6 Implementar SQSAdapter
    - Configurar SqsClient para polling de mensajes
    - Implementar listener para cola Standard (long polling 20s, batch 10)
    - Implementar listener para cola FIFO
    - Configurar visibility timeout 30s
    - Implementar lógica de confirmación (acknowledge) de mensajes
    - _Requirements: 2.1, 2.3, 2.5, 7.4_

  - [ ] 6.7 Implementar SQSConsumerErrorHandler
    - Validar formato de mensaje
    - Implementar reintentos con backoff exponencial para errores temporales
    - Mover a DLQ después de 3 reintentos o error permanente
    - Registrar errores en CloudWatch con nivel ERROR
    - Implementar idempotencia usando messageId
    - _Requirements: 2.4, 2.6, 6.1, 6.2, 6.5, 14.3_


  - [ ] 6.8 Implementar CloudWatchMetricsService
    - Publicar métricas: CorreosEnviados, CorreosFallidos, CorreosBloqueados, TiempoProcesamiento
    - Configurar namespace "MSCorreos"
    - Agregar dimensiones: Empresa, TipoNotificacion
    - _Requirements: 8.2, 16.14_

  - [ ] 6.9 Implementar StructuredLogger
    - Implementar logging estructurado con timestamp, nivel, mensaje, trace_id, empresa, tipo_notificacion
    - No registrar información sensible (contraseñas, claves completas)
    - Configurar niveles INFO, WARN, ERROR
    - _Requirements: 8.1, 8.6, 9.5_

  - [ ] 6.10 Implementar InputValidator
    - Validar formato de emails con regex RFC 5322
    - Detectar intentos de inyección SQL, XSS, command injection
    - Implementar sanitización de inputs
    - _Requirements: 9.6, 9.7_

  - [ ] 6.11 Implementar RateLimiter
    - Limitar envíos a 1000 correos/hora por empresa
    - Usar Bucket4j para rate limiting
    - Rechazar eventos que excedan el límite
    - _Requirements: 15.6_

  - [ ] 6.12 Configurar connection pooling
    - Configurar HikariCP: max 20 conexiones, min 5 idle, timeout 30s
    - Configurar cache de prepared statements
    - _Requirements: NF1.1_

  - [ ] 6.13 Configurar Secrets Manager
    - Cargar credenciales de BD desde AWS Secrets Manager
    - Configurar AWS SDK clients con roles IAM
    - _Requirements: 9.1, 9.2_

- [ ] 7. Checkpoint - Validar capa de infraestructura
  - Verificar que BlacklistService consulta BD correctamente
  - Verificar que SESAdapter puede enviar correos de prueba
  - Verificar que S3Adapter puede descargar/eliminar archivos
  - Verificar que métricas se publican en CloudWatch
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 8. Implementar capa de presentación (Presentation Layer)
  - [ ] 8.1 Implementar SNSListener
    - Crear endpoint POST /sns/notifications
    - Validar firma SNS para seguridad
    - Manejar SubscriptionConfirmation
    - Parsear eventos de tracking y delegar a ProcesarTrackingEventUseCase
    - _Requirements: 4.2_

  - [ ] 8.2 Implementar NotificacionesController
    - Crear endpoint GET /api/v1/notificaciones con filtros y paginación
    - Crear endpoint GET /api/v1/notificaciones/{id}
    - Validar API Key en header X-API-Key
    - Retornar HTTP 401 si API Key inválida
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.7, 11.8_

  - [ ] 8.3 Implementar ListaNegraController
    - Crear endpoint GET /api/v1/lista-negra con filtros y paginación
    - Crear endpoint POST /api/v1/lista-negra para agregar emails
    - Crear endpoint DELETE /api/v1/lista-negra/{email} para remover
    - Validar API Key
    - _Requirements: 16.10, 16.11, 16.12_

  - [ ] 8.4 Implementar HealthController
    - Crear endpoint GET /health
    - Verificar conectividad a BD (isValid)
    - Verificar conectividad a SQS (listQueues)
    - Retornar HTTP 200 si todo operativo, 503 si hay problemas
    - _Requirements: NF2.2_

  - [ ] 8.5 Implementar API Key authentication
    - Crear filtro para validar X-API-Key header
    - Configurar Spring Security para endpoints /api/v1/*
    - Permitir acceso sin autenticación a /health y /sns/notifications
    - _Requirements: 11.7, 11.8_

  - [ ] 8.6 Implementar AuditAspect
    - Registrar todos los accesos a API con: timestamp, usuario, endpoint, parámetros
    - Usar @Around para interceptar llamadas a controllers
    - _Requirements: NF4.5_


- [ ] 9. Implementar configuración y wiring
  - [ ] 9.1 Crear application.yml
    - Configurar datasource (PostgreSQL)
    - Configurar AWS (region, SQS URLs, SES configuration set, S3 bucket)
    - Configurar logging (CloudWatch log group)
    - Configurar Resilience4j (circuit breaker, retry)
    - Configurar cache (Caffeine)
    - _Requirements: NF5.1, NF5.2, NF5.3, NF5.4_

  - [ ] 9.2 Crear clases de configuración Spring
    - Crear ResilienceConfig con Circuit Breaker y Retry
    - Crear CacheConfig con Caffeine
    - Crear AsyncConfig con ThreadPoolTaskExecutor
    - Crear SecurityConfig con API Key filter
    - Crear SecretsConfig para cargar credenciales
    - _Requirements: 6.3, 6.4, 9.1_

  - [ ] 9.3 Configurar inyección de dependencias
    - Anotar servicios con @Service, @Component
    - Configurar @Autowired en constructores
    - Configurar @Bean para AWS clients (SqsClient, SesClient, S3Client, CloudWatchClient)
    - _Requirements: NF3.4_

  - [ ] 9.4 Crear Dockerfile
    - Usar imagen base Java 11
    - Copiar JAR de aplicación
    - Exponer puerto 8080
    - Configurar ENTRYPOINT
    - _Requirements: NF5.1_

  - [ ] 9.5 Crear docker-compose.yml para desarrollo local
    - Configurar PostgreSQL
    - Configurar LocalStack para simular AWS
    - Configurar MSCorreos con variables de entorno
    - _Requirements: NF5.3_

- [ ] 10. Checkpoint - Validar integración completa
  - Verificar que aplicación inicia correctamente
  - Verificar que health check retorna 200
  - Verificar que puede conectarse a BD y AWS
  - Verificar que API Key authentication funciona
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 11. Implementar tests unitarios
  - [ ]* 11.1 Tests para BlacklistService
    - Test: debeBloquearEmailConHardBounce
    - Test: debePermitirEmailNoEnListaNegra
    - Test: debeIncrementarContadorSoftBounce
    - Test: debeFiltrarDestinatariosBloqueadosDeListaMultiple
    - _Requirements: 16.2, 16.3, 16.8, 16.13_

  - [ ]* 11.2 Tests para TemplateService
    - Test: debeSustituirVariablesEnPlantilla
    - Test: debeUsarPlantillaPorDefectoSiNoExistePersonalizada
    - Test: debeValidarSintaxisDeVariables
    - _Requirements: 12.2, 12.4, 12.5, 12.6_

  - [ ]* 11.3 Tests para EmailService
    - Test: debeValidarFormatoEmail
    - Test: debeConstruirMensajeMIMEConAdjuntos
    - Test: debeRechazarAdjuntosMayoresA10MB
    - Test: debeSoportarMultiplesDestinatarios
    - _Requirements: 3.6, 3.7, 3.8, 13.6_

  - [ ]* 11.4 Tests para SESAdapter
    - Test: debeEnviarCorreoConTags
    - Test: debeReintentarEnThrottling
    - Test: debeAbrirCircuitBreakerDespuesDe50PorcentoFallos
    - Test: debeMoverADLQEnErrorPermanente
    - _Requirements: 3.3, 6.3, 6.4, 6.6, 14.2_

  - [ ]* 11.5 Tests para InputValidator
    - Test: debeValidarEmailsValidos
    - Test: debeRechazarEmailsInvalidos
    - Test: debeDetectarInyeccionSQL
    - Test: debeDetectarXSS
    - Test: debeSanitizarInputs
    - _Requirements: 3.7, 9.6, 9.7_

  - [ ]* 11.6 Tests para RateLimiter
    - Test: debePermitirHasta1000CorreosPorHora
    - Test: debeRechazarCorreosDespuesDeLimite
    - _Requirements: 15.6_


  - [ ]* 11.7 Tests para casos de uso
    - Test: EnviarNotificacionUseCase debe coordinar todos los servicios
    - Test: ProcesarTrackingEventUseCase debe agregar a lista negra en Bounce Permanent
    - Test: ProcesarTrackingEventUseCase debe incrementar contador en Soft Bounce
    - Test: ConsultarNotificacionesUseCase debe aplicar filtros correctamente
    - Test: GestionarListaNegraUseCase debe registrar en historial
    - _Requirements: 3.1, 4.6, 16.5, 16.8, 11.2, 16.16_

  - [ ]* 11.8 Tests para controllers
    - Test: NotificacionesController debe validar API Key
    - Test: NotificacionesController debe aplicar paginación
    - Test: ListaNegraController debe agregar email a lista negra
    - Test: HealthController debe verificar conectividad
    - _Requirements: 11.7, 11.8, 11.3, 16.10, NF2.2_

- [ ] 12. Implementar property-based tests
  - [ ]* 12.1 Property test: Publicación de eventos en SQS
    - **Property 1: Publicación de Eventos en SQS**
    - **Validates: Requirements 1.2, 2.1, 2.2**
    - Generar eventos aleatorios y verificar que aparecen en cola con todos los campos

  - [ ]* 12.2 Property test: Completitud de eventos
    - **Property 2: Completitud de Eventos de Notificación**
    - **Validates: Requirements 2.2, 13.2, 15.1**
    - Verificar que todos los eventos generados contienen campos obligatorios

  - [ ]* 12.3 Property test: Idempotencia de procesamiento
    - **Property 3: Idempotencia de Procesamiento**
    - **Validates: Requirements 2.6**
    - Procesar mismo evento múltiples veces y verificar mismo resultado

  - [ ]* 12.4 Property test: Validación de lista negra antes de envío
    - **Property 4: Validación de Lista Negra Antes de Envío**
    - **Validates: Requirements 3.1, 16.2, 16.3**
    - Verificar que consulta a lista negra ocurre antes de llamada a SES


  - [ ]* 12.5 Property test: Preservación de metadatos en tags SES
    - **Property 5: Preservación de Metadatos en Tags SES**
    - **Validates: Requirements 3.3, 4.8**
    - Verificar que tags SES contienen todos los metadatos del evento

  - [ ]* 12.6 Property test: Validación de formato de email
    - **Property 6: Validación de Formato de Email**
    - **Validates: Requirements 3.7**
    - Generar strings aleatorios y verificar que solo emails RFC 5322 son aceptados

  - [ ]* 12.7 Property test: Soporte de múltiples destinatarios
    - **Property 7: Soporte de Múltiples Destinatarios**
    - **Validates: Requirements 3.6**
    - Verificar que N destinatarios generan N llamadas a SES y N registros

  - [ ]* 12.8 Property test: Gestión completa de adjuntos
    - **Property 8: Gestión Completa de Adjuntos**
    - **Validates: Requirements 3.8, 13.3, 13.4**
    - Verificar ciclo completo: descarga S3 → adjuntar → enviar → eliminar

  - [ ]* 12.9 Property test: Registro de eventos de tracking
    - **Property 9: Registro de Eventos de Tracking**
    - **Validates: Requirements 4.3, 4.4, 4.5, 4.6, 4.7**
    - Verificar que todos los tipos de eventos se registran correctamente

  - [ ]* 12.10 Property test: Reintentos con backoff exponencial
    - **Property 10: Reintentos con Backoff Exponencial**
    - **Validates: Requirements 6.1, 6.6, 14.1, 14.3, 14.6**
    - Verificar que delays siguen patrón exponencial con jitter

  - [ ]* 12.11 Property test: Movimiento a DLQ por fallos
    - **Property 11: Movimiento a DLQ por Fallos**
    - **Validates: Requirements 2.4, 6.2, 14.2**
    - Verificar que mensajes fallidos van a DLQ después de 3 reintentos

  - [ ]* 12.12 Property test: Circuit breaker para SES
    - **Property 12: Circuit Breaker para SES**
    - **Validates: Requirements 6.3, 6.4**
    - Verificar que circuit breaker abre con 50% fallos en 10 llamadas


  - [ ]* 12.13 Property test: Logging estructurado completo
    - **Property 13: Logging Estructurado Completo**
    - **Validates: Requirements 8.1, 8.6, 9.5**
    - Verificar que logs contienen campos requeridos y no información sensible

  - [ ]* 12.14 Property test: Publicación de métricas
    - **Property 14: Publicación de Métricas**
    - **Validates: Requirements 8.2, 16.14, 15.7**
    - Verificar que métricas se publican para todos los eventos

  - [ ]* 12.15 Property test: Validación y sanitización de inputs
    - **Property 15: Validación y Sanitización de Inputs**
    - **Validates: Requirements 9.6, 9.7**
    - Verificar que inputs maliciosos son rechazados

  - [ ]* 12.16 Property test: Filtros de API de notificaciones
    - **Property 16: Filtros de API de Notificaciones**
    - **Validates: Requirements 11.2**
    - Verificar que resultados cumplen con todos los filtros aplicados

  - [ ]* 12.17 Property test: Paginación de API
    - **Property 17: Paginación de API**
    - **Validates: Requirements 11.3**
    - Verificar que páginas no tienen duplicados ni omisiones

  - [ ]* 12.18 Property test: Estructura de respuesta API
    - **Property 18: Estructura de Respuesta API**
    - **Validates: Requirements 11.4, 11.6**
    - Verificar que respuestas contienen todos los campos requeridos

  - [ ]* 12.19 Property test: Autenticación API con API Key
    - **Property 19: Autenticación API con API Key**
    - **Validates: Requirements 11.7, 11.8**
    - Verificar que requests sin API Key válida retornan 401

  - [ ]* 12.20 Property test: Sustitución de variables en plantillas
    - **Property 20: Sustitución de Variables en Plantillas**
    - **Validates: Requirements 12.2, 12.4**
    - Verificar que todas las variables son sustituidas correctamente


  - [ ]* 12.21 Property test: Carga de plantillas
    - **Property 21: Carga de Plantillas**
    - **Validates: Requirements 12.3, 12.5**
    - Verificar que siempre se usa alguna plantilla (personalizada o default)

  - [ ]* 12.22 Property test: Validación de sintaxis de plantillas
    - **Property 22: Validación de Sintaxis de Plantillas**
    - **Validates: Requirements 12.6**
    - Verificar que plantillas con sintaxis inválida son rechazadas

  - [ ]* 12.23 Property test: Validación de tamaño de adjuntos
    - **Property 23: Validación de Tamaño de Adjuntos**
    - **Validates: Requirements 13.6**
    - Verificar que adjuntos >10MB son rechazados

  - [ ]* 12.24 Property test: Priorización de mensajes
    - **Property 24: Priorización de Mensajes**
    - **Validates: Requirements 15.2**
    - Verificar que mensajes ALTA se procesan antes que MEDIA/BAJA

  - [ ]* 12.25 Property test: División de envíos masivos
    - **Property 25: División de Envíos Masivos**
    - **Validates: Requirements 15.5**
    - Verificar que eventos con >100 destinatarios se dividen correctamente

  - [ ]* 12.26 Property test: Rate limiting por empresa
    - **Property 26: Rate Limiting por Empresa**
    - **Validates: Requirements 15.6**
    - Verificar que no se superan 1000 correos/hora por empresa

  - [ ]* 12.27 Property test: Bloqueo por email en lista negra
    - **Property 27: Bloqueo por Email en Lista Negra**
    - **Validates: Requirements 16.3, 16.4**
    - Verificar que emails en lista negra son rechazados sin llamar a SES

  - [ ]* 12.28 Property test: Agregado automático a lista negra
    - **Property 28: Agregado Automático a Lista Negra**
    - **Validates: Requirements 16.5, 16.6**
    - Verificar que Hard Bounce y Complaint agregan a lista negra automáticamente


  - [ ]* 12.29 Property test: Bloqueo por soft bounces repetidos
    - **Property 29: Bloqueo por Soft Bounces Repetidos**
    - **Validates: Requirements 16.8**
    - Verificar que 3+ soft bounces en 30 días bloquean email

  - [ ]* 12.30 Property test: Filtrado de destinatarios en lista negra
    - **Property 30: Filtrado de Destinatarios en Lista Negra**
    - **Validates: Requirements 16.13**
    - Verificar que solo destinatarios válidos reciben correos

  - [ ]* 12.31 Property test: Historial de cambios en lista negra
    - **Property 31: Historial de Cambios en Lista Negra**
    - **Validates: Requirements 16.16**
    - Verificar que todos los cambios se registran en historial

  - [ ]* 12.32 Property test: Preservación de datos en migración
    - **Property 32: Preservación de Datos en Migración**
    - **Validates: Requirements 1.5**
    - Verificar que datos migrados mantienen integridad

- [ ] 13. Implementar tests de integración
  - [ ]* 13.1 Test de integración: Flujo completo de envío
    - Publicar evento en SQS → Consumer procesa → Valida lista negra → Envía SES → Registra BD
    - Usar Testcontainers para PostgreSQL y LocalStack para AWS
    - _Requirements: 1.2, 2.1, 3.1, 3.2, 16.2_

  - [ ]* 13.2 Test de integración: Flujo de tracking
    - Simular evento SNS → SNS Listener procesa → Registra en BD → Actualiza lista negra
    - _Requirements: 4.1, 4.2, 16.5, 16.6_

  - [ ]* 13.3 Test de integración: API REST
    - Consultar notificaciones con filtros → Verificar resultados
    - Agregar/remover de lista negra → Verificar historial
    - _Requirements: 11.1, 11.2, 16.10, 16.11, 16.16_


- [ ] 14. Checkpoint - Validar cobertura de tests
  - Ejecutar JaCoCo para verificar cobertura >= 80%
  - Verificar que todos los property tests pasan con 100 iteraciones
  - Verificar que tests de integración pasan con Testcontainers
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 15. Implementar migración de datos
  - [ ] 15.1 Crear script SQL de migración
    - Consolidar datos de tablas antiguas (car_pagos_notificaciones, etc.)
    - Mapear campos a cor_notificaciones
    - Agregar flag "MIGRADO_DE: tabla_origen" en n_observacion
    - Resolver conflictos de n_secuencial
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

  - [ ] 15.2 Crear script SQL para poblar lista negra inicial
    - Identificar correos con múltiples bounces permanentes
    - Identificar correos con complaints
    - Identificar correos con 3+ soft bounces en 30 días
    - Insertar en cor_lista_negra con tipo_bloqueo apropiado
    - _Requirements: 16.5, 16.6, 16.8_

  - [ ] 15.3 Ejecutar migración en ambiente de pruebas
    - Ejecutar scripts en transacción
    - Validar integridad: contar registros origen vs destino
    - Generar reporte de migración
    - _Requirements: 10.5, 10.6_

  - [ ] 15.4 Validar datos migrados
    - Verificar que todos los registros tienen campos requeridos
    - Verificar que n_observacion contiene flag de migración
    - Verificar que lista negra contiene correos problemáticos
    - _Requirements: 10.5_

- [ ] 16. Implementar productores en ShrimpSoftServer
  - [ ] 16.1 Crear EventoNotificacionBuilder
    - Builder para construir eventos con todos los campos
    - Validar campos obligatorios
    - _Requirements: 2.2_


  - [ ] 16.2 Implementar NotificacionProducer
    - Configurar SqsClient con credenciales IAM
    - Implementar publicar() que envía a cola Standard o FIFO según prioridad
    - Implementar subirAdjuntosS3() para archivos grandes
    - _Requirements: 2.1, 13.1, 15.1_

  - [ ] 16.3 Migrar módulo de Cartera
    - Reemplazar llamadas a UtilsMail con NotificacionProducer
    - Configurar tipo_notificacion = "NOTIFICAR_CUENTAS_POR_COBRAR"
    - Mantener doble escritura temporal (tabla antigua + SQS)
    - _Requirements: 1.2, 5.7_

  - [ ] 16.4 Migrar módulo de RRHH
    - Reemplazar llamadas a EnviarCorreoServiceImpl con NotificacionProducer
    - Configurar tipo_notificacion = "NOTIFICAR_ROL_PAGOS"
    - Mantener doble escritura temporal
    - _Requirements: 1.2, 5.6_

  - [ ] 16.5 Migrar módulo de Inventario
    - Reemplazar envíos de órdenes de compra con NotificacionProducer
    - Configurar tipo_notificacion = "NOTIFICAR_PROVEEDOR_ORDEN_COMPRA"
    - Mantener doble escritura temporal
    - _Requirements: 1.2, 5.5_

  - [ ] 16.6 Migrar módulo de Comprobantes Electrónicos
    - Reemplazar envíos de facturas/guías con NotificacionProducer
    - Configurar tipos: "NOTIFICAR_VENTA_ELECTRONICA_EMITIDA", "NOTIFICAR_GUIA_REMISION"
    - Incluir clave_acceso en metadatos
    - Mantener doble escritura temporal
    - _Requirements: 1.2, 5.1, 5.3_

  - [ ] 16.7 Migrar módulo de Contabilidad
    - Reemplazar notificaciones de errores con NotificacionProducer
    - Configurar tipo_notificacion = "NOTIFICAR_CONTABLE_ERRORES"
    - Mantener doble escritura temporal
    - _Requirements: 1.2, 5.9_

- [ ] 17. Checkpoint - Validar productores
  - Verificar que eventos se publican correctamente en SQS
  - Verificar que adjuntos se suben a S3
  - Verificar que doble escritura funciona (tabla antigua + SQS)
  - Ensure all tests pass, ask the user if questions arise.


- [ ] 18. Desplegar en ambiente de pruebas
  - [ ] 18.1 Construir imagen Docker
    - Ejecutar mvn clean package
    - Construir imagen con Dockerfile
    - Subir a ECR (Elastic Container Registry)
    - _Requirements: NF5.1_

  - [ ] 18.2 Crear Task Definition en ECS
    - Configurar CPU 512, Memory 1024
    - Configurar variables de entorno
    - Configurar secrets desde Secrets Manager
    - Configurar health check
    - _Requirements: 7.1, NF2.2_

  - [ ] 18.3 Crear Service en ECS con Auto Scaling
    - Configurar desiredCount = 2 (mínimo)
    - Configurar auto scaling: min 1, max 10
    - Configurar política de escalado basada en mensajes en cola
    - Configurar load balancer
    - _Requirements: 7.2, 7.3, 7.5, NF2.4, NF2.5_

  - [ ] 18.4 Configurar monitoreo
    - Verificar que logs aparecen en CloudWatch
    - Verificar que métricas se publican
    - Verificar que alarmas están activas
    - Configurar SNS topic para alertas
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

  - [ ] 18.5 Ejecutar pruebas end-to-end
    - Publicar evento de prueba en SQS
    - Verificar que correo se envía
    - Verificar que evento se registra en BD
    - Simular bounce y verificar que se agrega a lista negra
    - _Requirements: 1.1, 3.2, 4.3, 16.5_

- [ ] 19. Ejecutar migración de datos en producción
  - [ ] 19.1 Backup de base de datos
    - Crear snapshot de PostgreSQL
    - Verificar que backup es restaurable
    - _Requirements: 10.5_

  - [ ] 19.2 Ejecutar scripts de migración
    - Ejecutar script de creación de tablas
    - Ejecutar script de migración de datos históricos
    - Ejecutar script de población de lista negra inicial
    - _Requirements: 10.1, 10.2, 16.5, 16.6, 16.8_


  - [ ] 19.3 Validar migración
    - Ejecutar queries de validación
    - Verificar conteos: origen vs destino
    - Generar reporte de migración
    - _Requirements: 10.5, 10.6_

  - [ ] 19.4 Configurar tablas antiguas en modo solo lectura
    - Revocar permisos de INSERT/UPDATE/DELETE
    - Mantener permisos de SELECT
    - Documentar fecha de desactivación
    - _Requirements: 1.4, 10.7_

- [ ] 20. Desplegar en producción
  - [ ] 20.1 Desplegar MSCorreos en producción
    - Subir imagen Docker a ECR producción
    - Crear Task Definition y Service en ECS producción
    - Configurar auto scaling
    - Configurar load balancer con SSL/TLS
    - _Requirements: 7.1, 7.2, 9.3, NF2.1_

  - [ ] 20.2 Activar productores en modo dual
    - Habilitar publicación en SQS en todos los módulos
    - Mantener escritura en tablas antiguas (doble escritura)
    - Monitorear errores en CloudWatch
    - _Requirements: 1.2, 2.1_

  - [ ] 20.3 Monitorear sistema durante 2 semanas
    - Verificar que correos se envían correctamente
    - Verificar que lista negra funciona (no se envía a bloqueados)
    - Verificar que eventos de tracking se registran
    - Verificar que métricas y alarmas funcionan
    - Revisar logs diariamente para detectar problemas
    - _Requirements: 8.1, 8.2, 8.3, 16.2, 16.3_

  - [ ] 20.4 Validar performance
    - Verificar que tiempo de procesamiento < 2s (p95)
    - Verificar que latencia de API < 500ms (p95)
    - Verificar que sistema soporta 10,000 correos/hora
    - _Requirements: NF1.1, NF1.2, NF1.3, NF1.4_

  - [ ] 20.5 Validar disponibilidad
    - Verificar uptime >= 99.5%
    - Verificar que health checks funcionan
    - Verificar que auto scaling funciona bajo carga
    - _Requirements: NF2.1, NF2.2, NF2.3_


- [ ] 21. Desactivar escritura en tablas antiguas
  - [ ] 21.1 Desactivar doble escritura en productores
    - Remover código que escribe en tablas antiguas
    - Mantener solo publicación en SQS
    - Desplegar cambios en todos los módulos
    - _Requirements: 1.4_

  - [ ] 21.2 Configurar tablas antiguas en modo solo lectura
    - Revocar permisos de escritura
    - Mantener permisos de lectura por 90 días
    - Documentar fecha de desactivación
    - _Requirements: 10.7_

  - [ ] 21.3 Monitorear durante 1 semana adicional
    - Verificar que no hay errores por falta de tablas antiguas
    - Verificar que sistema funciona solo con nueva arquitectura
    - _Requirements: 1.1, 1.2_

- [ ] 22. Limpieza y documentación
  - [ ] 22.1 Eliminar código legacy
    - Eliminar UtilsMail.java
    - Eliminar EnviarCorreoServiceImpl.java
    - Eliminar referencias a tablas antiguas en código
    - _Requirements: 1.4_

  - [ ] 22.2 Archivar tablas antiguas
    - Exportar datos a archivos CSV
    - Crear backup final
    - Programar eliminación de tablas después de 90 días
    - _Requirements: 10.7_

  - [ ] 22.3 Documentar arquitectura
    - Crear diagrama de arquitectura actualizado
    - Documentar flujos de proceso
    - Documentar configuración de AWS
    - Documentar API REST (OpenAPI/Swagger)
    - _Requirements: NF3.5_

  - [ ] 22.4 Crear runbooks operacionales
    - Procedimiento para agregar/remover emails de lista negra
    - Procedimiento para reprocesar mensajes de DLQ
    - Procedimiento para escalar manualmente el servicio
    - Procedimiento para troubleshooting de errores comunes
    - _Requirements: NF2.1, NF3.1_


  - [ ] 22.5 Capacitar al equipo
    - Sesión de capacitación sobre nueva arquitectura
    - Sesión sobre uso de API REST
    - Sesión sobre monitoreo y troubleshooting
    - Entregar documentación y runbooks
    - _Requirements: NF3.1_

  - [ ] 22.6 Establecer procedimientos de mantenimiento
    - Definir proceso de actualización de plantillas
    - Definir proceso de revisión de lista negra
    - Definir proceso de análisis de métricas
    - Definir proceso de respuesta a alarmas
    - _Requirements: NF3.1, NF4.1_

- [ ] 23. Checkpoint final - Validar proyecto completo
  - Verificar que todos los requisitos funcionales están implementados
  - Verificar que todos los requisitos no funcionales se cumplen
  - Verificar que lista negra previene envíos a correos problemáticos
  - Verificar que no hay pérdida de datos históricos
  - Verificar que sistema es escalable y resiliente
  - Verificar que equipo está capacitado
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Las tareas marcadas con `*` son opcionales (tests) y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Property tests validan propiedades universales de correctness
- Unit tests validan casos específicos y edge cases
- La migración se realiza de forma gradual con doble escritura para minimizar riesgo
- El sistema implementa Clean Architecture para facilitar mantenimiento
- La lista negra es crítica: SIEMPRE se consulta ANTES de enviar a SES
- El monitoreo es esencial: logs, métricas y alarmas deben configurarse desde el inicio

## Criterios de Éxito

1. ✅ Todas las notificaciones se almacenan en correos.cor_notificaciones
2. ✅ MSCorreos consulta lista negra ANTES de enviar a Amazon SES
3. ✅ Sistema procesa eventos desde SQS y envía mediante SES
4. ✅ Eventos de tracking se registran automáticamente
5. ✅ Lista negra se actualiza automáticamente (Hard Bounce, Complaint, Soft Bounce repetidos)
6. ✅ Reintentos y DLQ funcionan correctamente
7. ✅ Auto scaling funciona según carga
8. ✅ Observabilidad completa con CloudWatch
9. ✅ API REST permite consultas y gestión de lista negra
10. ✅ Datos históricos migrados sin pérdida
11. ✅ Performance y disponibilidad cumplen requisitos no funcionales
12. ✅ No se envían correos a direcciones en lista negra
