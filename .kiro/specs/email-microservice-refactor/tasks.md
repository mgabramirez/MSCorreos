# Plan de Implementación: Email Microservice Refactor

## Resumen

Centralizar el envío de correos en MSCorreos con **Java 21**, **Spring Boot 3.2**, **arquitectura hexagonal** y **Spring Data JPA**. ShrimpSoftServer dejará de llamar directamente a AWS SES y usará MSCorreos vía REST HTTP.

## Tareas

- [x] 1. Cambios en base de datos — DDL y entidades JPA
  - [x] 1.1 Crear script SQL con ALTER TABLE para agregar `n_asunto` y `n_modulo` a `correos.cor_notificaciones`
    - Usar `ADD COLUMN IF NOT EXISTS` para que sea idempotente
    - Ubicar el script en `sql/updates/scripts/`
    - _Requisitos: 6.3_
  - [x] 1.2 Crear script SQL para la tabla `correos.cor_configuracion_empresa`
    - Incluir todos los campos del diseño: `emp_codigo`, `correo_emisor`, `nombre_emisor`, `configuration_set`, `region_aws`, `url_logo`, `html_header`, `html_footer`, `es_defecto`, `usr_codigo`, `usr_fecha`
    - Agregar constraint `UNIQUE` en `emp_codigo`
    - _Requisitos: 4.1_
  - [x] 1.3 Actualizar la entidad `CorreosNotificaciones.java` para mapear los nuevos campos `nAsunto` y `nModulo`
    - _Requisitos: 6.3_
  - [x] 1.4 Crear la entidad JPA `CorreosConfiguracionEmpresa.java` que mapee `correos.cor_configuracion_empresa`
    - _Requisitos: 4.1_
  - [x] 1.5 `PersistenceConfig` ya escanea `com.acosux` — sin cambios necesarios
    - _Requisitos: 4.1_

- [ ] 2. Migración a Java 21 y arquitectura hexagonal — restructurar MSCorreos
  - [ ] 2.1 Actualizar `MSCorreosApplication.java` para Java 21 / Spring Boot 3.2
    - Eliminar `exclude = HibernateJpaAutoConfiguration.class` (Spring Data JPA lo maneja)
    - _Stack: Java 21, Spring Boot 3.2_
  - [ ] 2.2 Crear la estructura de paquetes hexagonal:
    - `application/port/in/`, `application/port/out/`, `application/service/`
    - `domain/`
    - `infrastructure/in/web/`, `infrastructure/out/aws/`, `infrastructure/out/persistence/entity/`, `infrastructure/out/persistence/repository/`, `infrastructure/out/template/`
    - `shared/config/`
  - [ ] 2.3 Migrar entidades existentes (`CorreosNotificaciones`, `CorreosConfiguracionEmpresa`, `TipoNotificacion`) al nuevo paquete `domain/` y `infrastructure/out/persistence/entity/`
    - Cambiar imports de `javax.*` a `jakarta.*`
    - Convertir `CorreosNotificaciones` a entidad JPA con `jakarta.persistence.*`
  - [ ] 2.4 Reemplazar `PersistenceConfig.java` (Hibernate 4 manual) por configuración de Spring Data JPA en `application.properties`
    - Agregar `spring.datasource.*`, `spring.jpa.*` en `application.properties`
    - Eliminar `PersistenceConfig.java`, `GenericDao`, `GenericDaoImpl`, `GenericSQLDao`, `GenericSQLDaoImpl`

- [ ] 3. Dominio — Records Java 21 y puertos
  - [ ] 3.1 Crear los records del dominio en `domain/`:
    - `SolicitudCorreo.java` — record con validaciones `@NotBlank`
    - `AdjuntoCorreo.java` — record con `nombre`, `contenidoBase64`, `tipoMime`
    - `RespuestaEnvio.java` — record con `messageId`, `estado`
    - `Notificacion.java` — entidad de dominio (no JPA)
    - _Requisitos: 1.4_
  - [ ] 3.2 Agregar los 12 nuevos valores al enum `TipoNotificacion` (códigos 12–23)
    - `NOTIFICAR_PAGO_PROVEEDOR`, `NOTIFICAR_ANTICIPO_PROVEEDOR`, `NOTIFICAR_BENEFICIO_XIII`, `NOTIFICAR_BENEFICIO_XIV`, `NOTIFICAR_BENEFICIO_UTILIDADES`, `NOTIFICAR_ERROR_SISTEMA`, `NOTIFICAR_TICKET_SOPORTE`, `NOTIFICAR_DOCUMENTO_NO_AUTORIZADO`, `NOTIFICAR_PROVEEDOR_IMB`, `NOTIFICAR_ANULACION_VENTA`, `NOTIFICAR_ANULACION_RETENCION_COMPRA`, `NOTIFICAR_ORDEN_COMPRA_REGISTRADOR`
    - _Requisitos: 8.1_
  - [ ] 3.3 Implementar `fromCodigo(String codigo)` en `TipoNotificacion`
    - Lanzar `IllegalArgumentException` si el código no existe
    - _Requisitos: 8.3_
  - [ ] 3.4 Crear los puertos de salida en `application/port/out/`:
    - `CorreoSenderPort.java`
    - `NotificacionRepositoryPort.java`
    - `TemplateEnginePort.java`
    - `ConfiguracionEmpresaPort.java`
  - [ ] 3.5 Crear el puerto de entrada en `application/port/in/`:
    - `EnvioCorreoUseCase.java`

- [ ] 4. Adaptadores de persistencia — Spring Data JPA
  - [ ] 4.1 Crear entidades JPA con `jakarta.persistence.*` en `infrastructure/out/persistence/entity/`:
    - `NotificacionEntity.java` (mapea `correos.cor_notificaciones`)
    - `ConfiguracionEmpresaEntity.java` (mapea `correos.cor_configuracion_empresa`)
  - [ ] 4.2 Crear repositorios Spring Data en `infrastructure/out/persistence/repository/`:
    - `NotificacionJpaRepository.java` — extiende `JpaRepository<NotificacionEntity, Integer>`
    - `ConfiguracionEmpresaJpaRepository.java` — con método `findByEmpCodigo` y `findByEsDefectoTrue`
  - [ ] 4.3 Crear `JpaNotificacionAdapter.java` que implemente `NotificacionRepositoryPort`
    - _Requisitos: 6.1_
  - [ ] 4.4 Crear `JpaConfiguracionAdapter.java` que implemente `ConfiguracionEmpresaPort`
    - Si no existe config para la empresa, retornar la configuración por defecto
    - _Requisitos: 4.2, 4.3_

- [ ] 5. Adaptador de templates HTML — `HtmlTemplateAdapter`
  - [ ] 5.1 Crear la estructura de directorios de templates en `resources/templates/correos/`
    - `generico/base.html`, `generico/header.html`, `generico/footer.html`
    - `tipo/1.html` ... `tipo/23.html` (uno por TipoNotificacion)
    - `empresa/{ruc}/header.html` y `empresa/{ruc}/footer.html` (por empresa)
    - _Requisitos: 3.1_
  - [ ] 5.2 Implementar `HtmlTemplateAdapter.java` en `infrastructure/out/template/` que implemente `TemplateEnginePort`
    - Lógica de carga: empresa → tipo → genérico
    - Sustitución de variables `{{variable}}` con `String.replace()`
    - Inyectar header/footer de `ConfiguracionEmpresaEntity`
    - _Requisitos: 3.2, 3.3, 3.4, 3.5, 3.6_

- [ ] 6. Adaptador AWS SES — `AwsSesAdapter`
  - [ ] 6.1 Crear `AwsSesAdapter.java` en `infrastructure/out/aws/` que implemente `CorreoSenderPort`
    - Método `enviar`: construye mensaje MIME con adjuntos Base64 y llama a AWS SES `SendRawEmail`
    - Equivalente a `UtilsMail.envioCorreoPersonalizadoAmazonSES`
    - _Requisitos: 2.1_
  - [ ] 6.2 Implementar `enviarSimple` en `AwsSesAdapter` para correos sin adjuntos
    - Equivalente a `UtilsMail.envioErrorAmazonSES`
    - _Requisitos: 2.2_
  - [ ] 6.3 Implementar `esUnaEntidadVerificada`, `verificarEmail` y `listarEntidades`
    - _Requisitos: 2.3_
  - [ ] 6.4 Implementar `establecerTags` privado que construya los tags de SES desde `SolicitudCorreo`
    - Tags: `ows-tipo-notificacion`, `ows-empresa`, `ows-ruc`, `ows-clave`, `ows-clave-acceso`
    - _Requisitos: 2.5_

- [ ] 7. Caso de uso — `EnvioCorreoService`
  - [ ] 7.1 Implementar `EnvioCorreoService.java` en `application/service/` que implemente `EnvioCorreoUseCase`
    - Flujo: validar tipo → obtener config empresa → procesar template → enviar SES → registrar notificación
    - _Requisitos: 1.2, 1.3, 6.1_
  - [ ] 7.2 Agregar manejo de errores:
    - Error de AWS SES → registrar con `n_tipo = 'Error'` → lanzar excepción con HTTP 422
    - Tipo no reconocido → HTTP 400
    - _Requisitos: 5.1, 5.2, 5.3, 8.2_

- [ ] 8. Seguridad — `ApiKeyFilter`
  - [ ] 8.1 Crear `ApiKeyFilter.java` en `infrastructure/in/web/` que implemente `jakarta.servlet.Filter`
    - Validar `X-API-Key` solo para rutas `/api/v1/correos/`
    - HTTP 401 si ausente o incorrecto
    - _Requisitos: 10.1, 10.2, 10.3_
  - [ ] 8.2 Registrar el filtro con `FilterRegistrationBean` en `shared/config/BeanConfig.java`
    - _Requisitos: 10.1_
  - [ ] 8.3 Agregar `msCorreos.apiKey=CAMBIAR_POR_CLAVE_SEGURA` en `application.properties`
    - _Requisitos: 10.3_

- [ ] 9. Adaptador de entrada REST — `EnvioCorreoController`
  - [ ] 9.1 Crear `EnvioCorreoController.java` en `infrastructure/in/web/`
    - `POST /api/v1/correos/enviar` recibe `@RequestBody @Valid SolicitudCorreo`
    - Delega a `EnvioCorreoUseCase`
    - Retorna `RespuestaEnvio` HTTP 200
    - Maneja `MethodArgumentNotValidException` → HTTP 400
    - _Requisitos: 1.1, 1.2, 1.3_
  - [ ] 9.2 Migrar `CorreosController` (webhook SNS) al nuevo paquete `infrastructure/in/web/`
    - Renombrar a `SnsWebhookController.java`
    - Actualizar imports a `jakarta.*`
    - _Requisitos: 6.2_

- [ ] 10. Checkpoint — Verificar MSCorreos completo
  - Compilar el proyecto con `mvn compile`. Verificar que el endpoint `/api/v1/correos/enviar` responde. Consultar al usuario si surgen dudas.

- [ ] 11. Adaptación de ShrimpSoftServer — `CorreosHttpClient`
  - [ ] 11.1 Crear `SolicitudCorreoDTO.java` en ShrimpSoftServer con los mismos campos que el record `SolicitudCorreo` de MSCorreos
    - _Requisitos: 7.1_
  - [ ] 11.2 Crear `CorreosHttpClient.java` en ShrimpSoftServer
    - Usar `RestTemplate`, leer `msCorreos.url` y `msCorreos.apiKey` desde `application.properties`
    - Incluir header `X-API-Key` en cada solicitud
    - Lanzar `GeneralException` si MSCorreos retorna HTTP 4xx o 5xx
    - _Requisitos: 7.1, 7.2, 7.3, 10.4_
  - [ ] 11.3 Agregar `msCorreos.url` y `msCorreos.apiKey` en `application.properties` de ShrimpSoftServer
    - _Requisitos: 7.2_

- [ ] 12. Adaptación de ShrimpSoftServer — `EnviarCorreoServiceImpl`
  - [ ] 12.1 Refactorizar cada método de `EnviarCorreoServiceImpl` para construir `SolicitudCorreoDTO` y llamar a `CorreosHttpClient.enviar()`
    - Convertir adjuntos `File` a Base64 en `AdjuntoDTO`
    - Mantener la misma firma pública de `EnviarCorreoService`
    - _Requisitos: 7.1, 7.4, 7.5, 7.6_
  - [ ] 12.2 Eliminar todas las llamadas directas a `UtilsMail`
    - _Requisitos: 7.6_
  - [ ] 12.3 Eliminar la dependencia del SDK de AWS SES del `pom.xml` de ShrimpSoftServer
    - _Requisitos: 2.4_

- [ ] 13. Checkpoint — Verificar ShrimpSoftServer completo
  - Compilar con `mvn compile`. Verificar que no quedan referencias a `UtilsMail` ni al SDK de AWS SES. Consultar al usuario si surgen dudas.

- [ ] 14. Script SQL de migración de datos históricos
  - [ ] 14.1 Crear el script de migración en `sql/updates/scripts/` que migre los 9 tablas origen a `correos.cor_notificaciones`
    - Transacción `BEGIN` / `COMMIT`
    - `INSERT ... WHERE NOT EXISTS` para idempotencia
    - Poblar `n_modulo` con nombre de tabla origen
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.6_
  - [ ] 14.2 Agregar bloque `DO $$ ... $$` con conteo de registros migrados por tabla
    - _Requisitos: 9.5_

- [ ] 15. Checkpoint final — Verificar integración completa
  - Verificar flujo completo: ShrimpSoftServer → MSCorreos → AWS SES → SNS webhook → `cor_notificaciones`. Consultar al usuario si surgen dudas.

- [ ] 16. Integración de modelo de IA para generación de contenido de correos
  - Pendiente — se define en una iteración posterior con el usuario.

## Notas

- Las tareas marcadas con `*` son opcionales.
- **Java 21**: usar records para DTOs, `var` para inferencia de tipos, switch expressions.
- **Arquitectura hexagonal**: el dominio no depende de Spring ni de AWS. Los adaptadores implementan los puertos.
- **Jakarta EE 10**: todos los imports son `jakarta.*`, no `javax.*`.
- **Spring Data JPA**: reemplaza Hibernate 4 manual. No se necesita `PersistenceConfig.java`.


- [x] 1. Cambios en base de datos — DDL y entidades JPA
  - [x] 1.1 Crear script SQL con ALTER TABLE para agregar `n_asunto` y `n_modulo` a `correos.cor_notificaciones`
    - Usar `ADD COLUMN IF NOT EXISTS` para que sea idempotente
    - Ubicar el script en `sql/updates/`
    - _Requisitos: 6.3_
  - [x] 1.2 Crear script SQL para la tabla `correos.cor_configuracion_empresa`
    - Incluir todos los campos del diseño: `emp_codigo`, `correo_emisor`, `nombre_emisor`, `configuration_set`, `region_aws`, `url_logo`, `html_header`, `html_footer`, `es_defecto`, `usr_codigo`, `usr_fecha`
    - Agregar constraint `UNIQUE` en `emp_codigo`
    - _Requisitos: 4.1_
  - [x] 1.3 Actualizar la entidad `CorreosNotificaciones.java` para mapear los nuevos campos `nAsunto` y `nModulo`
    - Agregar anotaciones `@Column` correspondientes
    - _Requisitos: 6.3_
  - [x] 1.4 Crear la entidad JPA `CorreosConfiguracionEmpresa.java` que mapee `correos.cor_configuracion_empresa`
    - Incluir todos los campos con sus anotaciones `@Column`
    - _Requisitos: 4.1_
  - [x] 1.5 Actualizar `PersistenceConfig` para incluir el scan de `CorreosConfiguracionEmpresa`
    - _Requisitos: 4.1_

- [ ] 2. Nuevos tipos de notificación en `TipoNotificacion`
  - [ ] 2.1 Agregar los 12 nuevos valores al enum `TipoNotificacion` (códigos 12–23)
    - `NOTIFICAR_PAGO_PROVEEDOR`, `NOTIFICAR_ANTICIPO_PROVEEDOR`, `NOTIFICAR_BENEFICIO_XIII`, `NOTIFICAR_BENEFICIO_XIV`, `NOTIFICAR_BENEFICIO_UTILIDADES`, `NOTIFICAR_ERROR_SISTEMA`, `NOTIFICAR_TICKET_SOPORTE`, `NOTIFICAR_DOCUMENTO_NO_AUTORIZADO`, `NOTIFICAR_PROVEEDOR_IMB`, `NOTIFICAR_ANULACION_VENTA`, `NOTIFICAR_ANULACION_RETENCION_COMPRA`, `NOTIFICAR_ORDEN_COMPRA_REGISTRADOR`
    - _Requisitos: 8.1_
  - [ ] 2.2 Implementar el método estático `fromCodigo(String codigo)` en `TipoNotificacion`
    - Debe lanzar `IllegalArgumentException` si el código no existe
    - _Requisitos: 8.3_
  - [ ]* 2.3 Escribir pruebas unitarias para `TipoNotificacion.fromCodigo`
    - Verificar todos los códigos válidos (1–23)
    - Verificar que un código inválido lanza `IllegalArgumentException`
    - Verificar que un `tipoNotificacion` no reconocido retorna HTTP 400 desde el controlador
    - _Requisitos: 8.2, 8.3_

- [ ] 3. DTOs del endpoint de envío
  - [ ] 3.1 Crear `AdjuntoDTO.java` con campos `nombre`, `contenidoBase64` y `tipoMime`
    - _Requisitos: 1.4_
  - [ ] 3.2 Crear `SolicitudCorreo.java` con todos los campos requeridos y validaciones `@NotBlank` / `@NotNull` en los obligatorios
    - Campos: `empresa`, `tipoNotificacion`, `destinatarios`, `destinatariosCC`, `asunto`, `cuerpoTextoPlano`, `parametrosTemplate`, `adjuntos`, `claveAcceso`, `ruc`, `clave`
    - _Requisitos: 1.4_
  - [ ] 3.3 Crear `RespuestaEnvio.java` con campos `messageId` y `estado`
    - _Requisitos: 1.2_

- [ ] 4. Capa de acceso a datos — `ConfiguracionEmpresaDao`
  - [ ] 4.1 Crear la interfaz `ConfiguracionEmpresaDao.java` con el método `findByEmpCodigo(String empCodigo)` y `findDefault()`
    - _Requisitos: 4.1, 4.2, 4.3_
  - [ ] 4.2 Implementar `ConfiguracionEmpresaDaoImpl.java` usando `JdbcTemplate` o el mecanismo de persistencia existente en MSCorreos
    - _Requisitos: 4.1_
  - [ ]* 4.3 Escribir pruebas unitarias para `ConfiguracionEmpresaDaoImpl`
    - Verificar que retorna la configuración correcta por `emp_codigo`
    - Verificar que retorna la configuración por defecto cuando no existe la empresa
    - _Requisitos: 4.2, 4.3_

- [ ] 5. Servicio de configuración de empresa — `ConfiguracionEmpresaService`
  - [ ] 5.1 Crear `ConfiguracionEmpresaService.java` que use `ConfiguracionEmpresaDao` para obtener la configuración por empresa
    - Si no existe configuración para la empresa, usar la configuración por defecto y registrar advertencia en log
    - _Requisitos: 4.2, 4.3_
  - [ ] 5.2 Agregar validación de correo emisor verificado en AWS SES dentro de `ConfiguracionEmpresaService`
    - Llamar a `AwsSesService.esUnaEntidadVerificada` antes de retornar la configuración
    - _Requisitos: 4.4_

- [ ] 6. Integración AWS SES — `AwsSesService`
  - [ ] 6.1 Crear `AwsSesService.java` con el método `enviarCorreo(SolicitudCorreo, String htmlBody, CorreosConfiguracionEmpresa)` que construya y envíe el mensaje MIME con adjuntos a AWS SES
    - Equivalente a `UtilsMail.envioCorreoPersonalizadoAmazonSES`
    - _Requisitos: 2.1_
  - [ ] 6.2 Implementar el método `enviarCorreoSimple` en `AwsSesService` para correos sin adjuntos
    - Equivalente a `UtilsMail.envioErrorAmazonSES`
    - _Requisitos: 2.2_
  - [ ] 6.3 Implementar los métodos `esUnaEntidadVerificada`, `verificarEmail` y `listarEntidades` en `AwsSesService`
    - _Requisitos: 2.3_
  - [ ] 6.4 Implementar el método privado `establecerTags` en `AwsSesService` que construya los tags de SES a partir de `SolicitudCorreo`
    - Tags: `ows-tipo-notificacion`, `ows-empresa`, `ows-ruc`, `ows-clave`, `ows-clave-acceso`
    - _Requisitos: 2.5_

- [ ] 7. Sistema de templates HTML — `TemplateService`
  - [ ] 7.1 Crear la estructura de directorios de templates en `resources/templates/correos/` con subdirectorios `generico/`, `tipo/` y `empresa/`
    - Crear los archivos `header.html`, `footer.html` y `base.html` genéricos con contenido de ejemplo
    - _Requisitos: 3.1_
  - [ ] 7.2 Crear un archivo HTML de template por cada tipo de notificación existente (tipos 1–11) en `resources/templates/correos/tipo/`
    - Usar variables `{{nombreCliente}}`, `{{numeroComprobante}}`, `{{tipoComprobante}}`, `{{claveAcceso}}`, `{{valor}}`, `{{nombreEmisor}}`, `{{rucEmisor}}`, `{{direccionEmisor}}`, `{{telefonoEmisor}}`
    - _Requisitos: 3.1, 3.3_
  - [ ] 7.3 Implementar `TemplateService.java` con el método `procesarTemplate(String tipoNotificacion, String ruc, Map<String,Object> parametros)`
    - Lógica de carga: primero busca template de empresa (`empresa/{ruc}/`), luego template por tipo (`tipo/{codigo}_{nombre}.html`), finalmente template genérico
    - Sustitución de variables con `String.replace()` usando sintaxis `{{variable}}`
    - Si el template no existe, usar genérico y registrar advertencia en log
    - _Requisitos: 3.2, 3.3, 3.4, 3.5_
  - [ ] 7.4 Agregar soporte de header y footer por empresa en `TemplateService`
    - Inyectar `html_header` y `html_footer` de `CorreosConfiguracionEmpresa` en el template antes del envío
    - _Requisitos: 3.6_
  - [ ]* 7.5 Escribir pruebas unitarias para `TemplateService`
    - Verificar sustitución correcta de variables
    - Verificar fallback a template genérico cuando no existe el template específico
    - Verificar que se usa el template de empresa cuando existe
    - _Requisitos: 3.2, 3.3, 3.4, 3.5_

- [ ] 8. Seguridad — `ApiKeyFilter`
  - [ ] 8.1 Crear `ApiKeyFilter.java` que implemente `javax.servlet.Filter`
    - Validar el header `X-API-Key` solo para rutas que comiencen con `/api/v1/correos/`
    - Retornar HTTP 401 si el header está ausente o el valor no coincide con `msCorreos.apiKey`
    - Leer el valor esperado desde `@Value("${msCorreos.apiKey}")`
    - _Requisitos: 10.1, 10.2, 10.3_
  - [ ] 8.2 Registrar `ApiKeyFilter` en la configuración de filtros de Spring (web.xml o `FilterRegistrationBean`)
    - _Requisitos: 10.1_
  - [ ] 8.3 Agregar la propiedad `msCorreos.apiKey=CAMBIAR_POR_CLAVE_SEGURA` en `application.properties` de MSCorreos
    - _Requisitos: 10.3_
  - [ ]* 8.4 Escribir pruebas unitarias para `ApiKeyFilter`
    - Verificar que una solicitud sin `X-API-Key` retorna 401
    - Verificar que una solicitud con API Key incorrecta retorna 401
    - Verificar que una solicitud con API Key correcta pasa el filtro
    - _Requisitos: 10.1, 10.2_

- [ ] 9. Servicio de envío — `EnvioCorreoService` y `EnvioCorreoServiceImpl`
  - [ ] 9.1 Crear la interfaz `EnvioCorreoService.java` con el método `enviar(SolicitudCorreo solicitud)`
    - _Requisitos: 1.1_
  - [ ] 9.2 Implementar `EnvioCorreoServiceImpl.java` que orqueste el flujo completo:
    1. Validar campos obligatorios de `SolicitudCorreo`
    2. Obtener `CorreosConfiguracionEmpresa` via `ConfiguracionEmpresaService`
    3. Procesar template HTML via `TemplateService`
    4. Enviar correo via `AwsSesService`
    5. Registrar envío exitoso en `correos.cor_notificaciones` via `CorreosNotificacionDao`
    - _Requisitos: 1.2, 1.3, 6.1_
  - [ ] 9.3 Agregar manejo de errores en `EnvioCorreoServiceImpl`:
    - Si AWS SES retorna error, registrar en `cor_notificaciones` con `n_tipo = 'Error'` y retornar HTTP 422
    - Registrar en log cada envío fallido con empresa, tipo de notificación y mensaje de error
    - _Requisitos: 5.1, 5.2, 5.3_
  - [ ] 9.4 Agregar validación de `tipoNotificacion` en `EnvioCorreoServiceImpl` usando `TipoNotificacion.fromCodigo`
    - Retornar HTTP 400 con mensaje `'Tipo de notificación no reconocido: {valor}'` si no existe
    - _Requisitos: 8.2_
  - [ ]* 9.5 Escribir pruebas unitarias para `EnvioCorreoServiceImpl`
    - Verificar flujo exitoso: template procesado, correo enviado, registro insertado
    - Verificar que un error de AWS SES genera registro con `n_tipo = 'Error'`
    - Verificar que campos obligatorios faltantes retornan HTTP 400
    - _Requisitos: 1.2, 1.3, 5.1, 5.2_

- [ ] 10. Controlador REST — `EnvioCorreoController`
  - [ ] 10.1 Crear `EnvioCorreoController.java` con el endpoint `POST /api/v1/correos/enviar`
    - Recibir `@RequestBody @Valid SolicitudCorreo`
    - Delegar a `EnvioCorreoService.enviar()`
    - Retornar `RespuestaEnvio` con HTTP 200 en caso exitoso
    - Manejar `MethodArgumentNotValidException` para retornar HTTP 400 con mensaje descriptivo
    - _Requisitos: 1.1, 1.2, 1.3_
  - [ ]* 10.2 Escribir pruebas de integración para `EnvioCorreoController`
    - Verificar HTTP 200 con `messageId` en respuesta exitosa
    - Verificar HTTP 400 cuando faltan campos obligatorios
    - Verificar HTTP 401 cuando falta el header `X-API-Key`
    - Verificar HTTP 422 cuando AWS SES rechaza el correo
    - _Requisitos: 1.2, 1.3, 5.2, 10.2_

- [ ] 11. Checkpoint — Verificar MSCorreos completo
  - Asegurarse de que todos los tests pasen. Verificar que el endpoint `/api/v1/correos/enviar` responde correctamente con un cliente REST (ej. curl o Postman). Consultar al usuario si surgen dudas.

- [ ] 12. Adaptación de ShrimpSoftServer — `CorreosHttpClient`
  - [ ] 12.1 Crear `SolicitudCorreoDTO.java` en ShrimpSoftServer con los mismos campos que `SolicitudCorreo` de MSCorreos (incluyendo `AdjuntoDTO`)
    - _Requisitos: 7.1_
  - [ ] 12.2 Crear `CorreosHttpClient.java` en ShrimpSoftServer
    - Leer `msCorreos.url` y `msCorreos.apiKey` desde `application.properties`
    - Usar `RestTemplate` para hacer `POST` a `/api/v1/correos/enviar`
    - Incluir header `X-API-Key` en cada solicitud
    - Lanzar `GeneralException` si MSCorreos retorna HTTP 4xx o 5xx
    - _Requisitos: 7.1, 7.2, 7.3, 10.4_
  - [ ] 12.3 Agregar las propiedades `msCorreos.url` y `msCorreos.apiKey` en `application.properties` de ShrimpSoftServer
    - _Requisitos: 7.2_
  - [ ]* 12.4 Escribir pruebas unitarias para `CorreosHttpClient`
    - Verificar que se incluye el header `X-API-Key` en la solicitud
    - Verificar que un HTTP 4xx lanza `GeneralException`
    - _Requisitos: 7.2, 7.3_

- [ ] 13. Adaptación de ShrimpSoftServer — `EnviarCorreoServiceImpl`
  - [ ] 13.1 Refactorizar `EnviarCorreoServiceImpl` para que cada método construya una `SolicitudCorreoDTO` y llame a `CorreosHttpClient.enviar()` en lugar de `UtilsMail`
    - Incluir la conversión de adjuntos `File` a Base64 en `AdjuntoDTO`
    - Mantener la misma firma pública de `EnviarCorreoService` sin cambios
    - _Requisitos: 7.1, 7.4, 7.5, 7.6_
  - [ ] 13.2 Eliminar todas las llamadas directas a `UtilsMail` de `EnviarCorreoServiceImpl`
    - _Requisitos: 7.6_
  - [ ] 13.3 Eliminar la dependencia del SDK de AWS SES del `pom.xml` de ShrimpSoftServer
    - Verificar que no queden otras clases que usen el SDK de AWS SES directamente
    - _Requisitos: 2.4_
  - [ ]* 13.4 Escribir pruebas unitarias para `EnviarCorreoServiceImpl` refactorizado
    - Verificar que cada método construye correctamente la `SolicitudCorreoDTO`
    - Verificar que los adjuntos se codifican en Base64
    - Verificar que `GeneralException` se propaga correctamente
    - _Requisitos: 7.1, 7.3, 7.4_

- [ ] 14. Checkpoint — Verificar ShrimpSoftServer completo
  - Asegurarse de que todos los tests pasen y que el proyecto compila sin errores. Verificar que no quedan referencias a `UtilsMail` ni al SDK de AWS SES en ShrimpSoftServer. Consultar al usuario si surgen dudas.

- [ ] 15. Script SQL de migración de datos históricos
  - [ ] 15.1 Crear el script de migración en `sql/updates/` que migre los registros de las 9 tablas origen a `correos.cor_notificaciones`
    - Envolver todo en una transacción (`BEGIN` / `COMMIT`)
    - Usar `INSERT ... WHERE NOT EXISTS` para idempotencia, con clave de deduplicación basada en `n_modulo` + identificador original
    - Poblar `n_modulo` con el nombre de esquema y tabla de origen
    - Mapear `n_tipo_notificacion` según la tabla de mapeo del diseño
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.6_
  - [ ] 15.2 Agregar bloque `DO $$ ... $$` al final del script que imprima el conteo de registros migrados por tabla de origen
    - _Requisitos: 9.5_
  - [ ] 15.3 Verificar que el script es idempotente ejecutándolo dos veces en un entorno de prueba y confirmando que no genera duplicados
    - _Requisitos: 9.6_

- [ ] 16. Checkpoint final — Verificar integración completa
  - Asegurarse de que todos los tests de ambos proyectos pasen. Verificar el flujo completo: ShrimpSoftServer → MSCorreos → AWS SES → SNS webhook → `cor_notificaciones`. Consultar al usuario si surgen dudas.

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido.
- Cada tarea referencia los requisitos específicos para trazabilidad.
- Los checkpoints garantizan validación incremental antes de continuar.
- El lenguaje de implementación es **Java** con Spring Boot, siguiendo las convenciones existentes en MSCorreos y ShrimpSoftServer.
- No agregar nuevas dependencias al `pom.xml` de MSCorreos salvo que sea estrictamente necesario; usar `String.replace()` para templates en lugar de motores externos.
