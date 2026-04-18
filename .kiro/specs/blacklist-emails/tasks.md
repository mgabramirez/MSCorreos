# Implementation Plan: Blacklist Emails Component

## Overview

Componente global reutilizable para gestión de blacklist de correos electrónicos. Implementación en Java con Spring Boot, PostgreSQL y Redis para cache. El componente es GLOBAL sin asociación a empresas, permitiendo su uso centralizado por todos los servicios del ecosistema.

## Tasks

- [x] 1. Implementar capa de base de datos y entidad
  - [x] 1.1 Crear migración SQL para tabla blacklist_emails con índices
    - Crear tabla con campos: id, email, razon, fecha_bloqueo, bloqueado_por, activo, campos de auditoría
    - Crear índice único: idx_blacklist_email_unico en email WHERE activo = true
    - Crear índices: idx_blacklist_email_activo, idx_blacklist_fecha_bloqueo
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [x] 1.2 Crear entidad JPA BlacklistEmail
    - Implementar entidad con anotaciones JPA
    - Incluir todos los campos con tipos correctos (LocalDateTime, Boolean, String)
    - Configurar estrategia de generación de ID (IDENTITY)
    - _Requirements: 7.1_
  
  - [x] 1.3 Crear interfaz BlacklistEmailRepository
    - Extender JpaRepository<BlacklistEmail, Long>
    - Implementar métodos: findByEmail, existsByEmailAndActivoTrue, findByActivoTrue, countByActivoTrue
    - _Requirements: 1.3, 4.1, 5.1_

- [x] 2. Implementar capa de cache con Redis
  - [x] 2.1 Crear componente BlacklistEmailCache
    - Implementar métodos: get, put, invalidate
    - Configurar TTL de 24 horas para entradas de cache
    - Usar prefijo de clave "blacklist:email:"
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 3. Implementar capa de servicio
  - [x] 3.1 Crear BlacklistEmailService con método isEmailBloqueado
    - Implementar patrón cache-aside: primero cache, luego BD
    - Normalizar email (lowercase y trim)
    - Actualizar cache después de consulta a BD
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_
  
  - [x] 3.2 Implementar método agregarEmail en BlacklistEmailService
    - Validar si email ya existe con activo = true (lanzar EmailYaExisteException)
    - Reactivar email si existe con activo = false
    - Crear nuevo registro si no existe
    - Establecer campos de auditoría (usuario_creacion, fecha_creacion)
    - Actualizar cache después de operación exitosa
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
  
  - [x] 3.3 Implementar método eliminarEmail en BlacklistEmailService
    - Buscar email (lanzar EmailNoEncontradoException si no existe)
    - Establecer activo = false (soft delete)
    - Actualizar campos de auditoría (usuario_actualizacion, fecha_actualizacion)
    - Invalidar entrada de cache
    - _Requirements: 3.1, 3.2, 3.3, 3.4_
  
  - [x] 3.4 Implementar métodos listarEmails y countActiveEmails
    - listarEmails: retornar Page<BlacklistEmail> con activo = true
    - Soportar paginación y ordenamiento por fechaBloqueo
    - countActiveEmails: retornar count de registros con activo = true
    - _Requirements: 4.1, 4.2, 4.3, 5.1_

- [x] 4. Implementar endpoints básicos del controlador
  - [x] 4.1 Crear BlacklistEmailController con endpoint GET /{email}
    - Implementar verificación de email bloqueado
    - Retornar BlacklistCheckResponse con email y estado bloqueado
    - _Requirements: 8.1_
  
  - [x] 4.2 Implementar endpoint POST /api/v1/blacklist-emails
    - Validar request con @Valid
    - Extraer usuario de header X-Usuario
    - Retornar 201 Created con BlacklistEmailResponse
    - _Requirements: 8.2, 8.6_
  
  - [x] 4.3 Implementar endpoint DELETE /api/v1/blacklist-emails/{email}
    - Extraer usuario de header X-Usuario
    - Retornar 204 No Content
    - _Requirements: 8.3_

- [ ] 5. Completar endpoints de listado y conteo
  - [x] 5.1 Implementar endpoint GET /api/v1/blacklist-emails (paginado)
    - Usar @PageableDefault con size=20 y sort por fechaBloqueo
    - Convertir Page<BlacklistEmail> a Page<BlacklistEmailResponse>
    - Retornar 200 OK con datos paginados
    - _Requirements: 8.4, 4.1, 4.2, 4.3_
  
  - [x] 5.2 Implementar endpoint GET /api/v1/blacklist-emails/count
    - Llamar a service.countActiveEmails()
    - Retornar 200 OK con objeto { count: number }
    - _Requirements: 8.5, 5.1_

- [ ] 6. Crear DTOs y excepciones personalizadas
  - [x] 6.1 Crear BlacklistCheckResponse DTO
    - Campos: email (String), bloqueado (boolean), timestamp (LocalDateTime)
    - Constructor y getters/setters
    - _Requirements: 8.1_
  
  - [x] 6.2 Crear BlacklistEmailRequest DTO
    - Campos: email (@NotBlank @Email), razon (@Size(max=500))
    - Validaciones con Bean Validation
    - _Requirements: 8.2, 8.6_
  
  - [x] 6.3 Crear BlacklistEmailResponse DTO
    - Campos: id, email, razon, fechaBloqueo, bloqueadoPor, activo
    - Método helper toResponse en controller para conversión
    - _Requirements: 4.3, 8.2, 8.4_
  
  - [x] 6.4 Crear EmailYaExisteException
    - Extender RuntimeException
    - Anotar con @ResponseStatus(HttpStatus.CONFLICT)
    - _Requirements: 2.2_
  
  - [x] 6.5 Crear EmailNoEncontradoException
    - Extender RuntimeException
    - Anotar con @ResponseStatus(HttpStatus.NOT_FOUND)
    - _Requirements: 3.2_

- [ ] 7. Configurar propiedades y dependencias
  - [x] 7.1 Agregar configuración de Redis en application.properties
    - spring.redis.host, spring.redis.port
    - Configuración de timeout y pool de conexiones
    - _Requirements: 6.1_
  
  - [x] 7.2 Agregar configuración de pool de conexiones de BD
    - HikariCP: maximum-pool-size=20, minimum-idle=5, connection-timeout=30000
    - _Requirements: Performance optimization_
  
  - [x] 7.3 Verificar dependencias en pom.xml
    - spring-boot-starter-web, spring-boot-starter-data-jpa
    - spring-boot-starter-data-redis, spring-boot-starter-validation
    - postgresql driver
    - _Requirements: All_

- [ ] 8. Integración con MSCorreos
  - [x] 8.1 Crear BlacklistEmailClient Feign interface
    - Anotar con @FeignClient(name="blacklist-emails-service")
    - Método checkEmail con @GetMapping
    - Configurar URL del servicio en properties
    - _Requirements: 9.1_
  
  - [x] 8.2 Actualizar NotificacionService para verificar blacklist
    - Inyectar BlacklistEmailClient
    - Verificar blacklist antes de enviar correo
    - Saltar envío si email está bloqueado y registrar log
    - _Requirements: 9.2, 9.3_

- [ ]* 9. Testing y validación
  - [ ]* 9.1 Escribir tests unitarios para BlacklistEmailService
    - Test para isEmailBloqueado con cache hit/miss
    - Test para agregarEmail con casos: nuevo, existente activo, existente inactivo
    - Test para eliminarEmail con casos: existente, no existente
    - Test para normalización de email
    - _Requirements: 1.6, 2.1, 2.2, 2.3, 3.1, 3.2_
  
  - [ ]* 9.2 Escribir tests unitarios para BlacklistEmailController
    - Test para cada endpoint con MockMvc
    - Validar códigos de respuesta HTTP
    - Validar estructura de respuestas JSON
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_
  
  - [ ]* 9.3 Escribir property test para normalización de email
    - **Property 1: Email Normalization**
    - **Validates: Requirements 1.6**
    - Generar emails aleatorios con mayúsculas/minúsculas y espacios
    - Verificar que siempre se normalicen a lowercase y trim
  
  - [ ]* 9.4 Escribir property test para patrón cache-aside
    - **Property 2: Cache-Aside Pattern**
    - **Validates: Requirements 1.1, 1.2, 1.3**
    - Verificar que cache se consulta antes que BD
    - Verificar que BD se consulta solo en cache miss
  
  - [ ]* 9.5 Escribir property test para round-trip email check
    - **Property 14: Round-Trip Email Check**
    - **Validates: Requirements 1.4, 2.1**
    - Agregar email a blacklist y verificar que isEmailBloqueado retorna true
  
  - [ ]* 9.6 Escribir tests de integración para API endpoints
    - Test end-to-end con base de datos H2 en memoria
    - Test de flujo completo: agregar, verificar, listar, eliminar
    - _Requirements: All API endpoints_

- [x] 10. Checkpoint final
  - Verificar que todos los endpoints funcionan correctamente
  - Verificar que la integración con Redis funciona
  - Verificar que la integración con MSCorreos funciona
  - Asegurar que todos los tests pasan (si se implementaron)
  - Preguntar al usuario si hay dudas o ajustes necesarios

## Notes

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Los property tests validan propiedades de corrección universales
- Los tests unitarios validan ejemplos específicos y casos borde
- El componente es GLOBAL sin tenant_id, permitiendo uso centralizado