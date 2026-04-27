# Documento de Requisitos

## Introducción

Esta refactorización centraliza **todo el envío de correos electrónicos** en el microservicio **MSCorreos**. Actualmente, `ShrimpSoftServer` envía correos directamente a AWS SES a través de `UtilsMail` y `EnviarCorreoServiceImpl`. Tras la refactorización, `ShrimpSoftServer` delegará el envío mediante llamadas REST HTTP a `MSCorreos`, que pasará a ser el único componente con acceso directo a AWS SES.

El alcance incluye:
- Nuevos endpoints REST en MSCorreos para recibir solicitudes de envío.
- Migración de la lógica de envío (AWS SES) desde `UtilsMail` a MSCorreos.
- Sistema de templates HTML externalizado del código Java.
- Soporte para adjuntos (PDF, XML).
- Manejo de errores.
- Configuración de AWS SES por empresa.
- Adaptación de `EnviarCorreoServiceImpl` en ShrimpSoftServer para llamar a MSCorreos vía HTTP.
- Script SQL de migración de datos históricos de 9 tablas a `correos.cor_notificaciones`.

---

## Glosario

- **MSCorreos**: Microservicio Spring Boot dedicado exclusivamente al envío y registro de correos electrónicos.
- **ShrimpSoftServer**: Backend principal Java/Spring Boot que orquesta la lógica de negocio y delega el envío de correos a MSCorreos.
- **AWS_SES**: Amazon Simple Email Service. Servicio externo de envío de correos electrónicos.
- **AWS_SNS**: Amazon Simple Notification Service. Servicio que envía webhooks de eventos de correo (Send, Delivery, Bounce, Complaint, Open) a MSCorreos.
- **EnviarCorreoService**: Interfaz Java en ShrimpSoftServer que define los métodos de envío de correo disponibles para el resto del backend.
- **EnviarCorreoServiceImpl**: Implementación de `EnviarCorreoService` en ShrimpSoftServer. Tras la refactorización, delega a MSCorreos vía HTTP en lugar de llamar directamente a AWS SES.
- **UtilsMail**: Clase utilitaria estática en ShrimpSoftServer que actualmente contiene la integración directa con AWS SES. Será eliminada o vaciada tras la migración.
- **SolicitudCorreo**: DTO (Data Transfer Object) JSON que ShrimpSoftServer envía a MSCorreos con todos los datos necesarios para el envío de un correo.
- **Template_HTML**: Archivo de plantilla HTML almacenado en el sistema de archivos o classpath de MSCorreos, usado para construir el cuerpo de los correos.
- **cor_notificaciones**: Tabla central en el esquema `correos` de PostgreSQL donde MSCorreos registra todos los eventos de correo (envíos, entregas, rebotes, quejas, aperturas).
- **TipoNotificacion**: Enumeración que clasifica el propósito de cada correo (venta electrónica, rol de pagos, cuentas por cobrar, etc.).
- **Adjunto**: Archivo binario (PDF o XML) que se incluye en el correo. ShrimpSoftServer lo envía codificado en Base64 dentro de la `SolicitudCorreo`.
- **ConfiguracionEmpresa**: Conjunto de parámetros de AWS SES específicos por empresa: correo emisor, nombre emisor, configuration set de SES, región AWS.
- **Cliente_HTTP**: Componente en ShrimpSoftServer (basado en `RestTemplate` o `WebClient`) que realiza las llamadas REST a MSCorreos.
---

## Requisitos

### Requisito 1: Endpoint de envío de correo en MSCorreos

**User Story:** Como desarrollador de ShrimpSoftServer, quiero un endpoint REST en MSCorreos al que pueda enviar una solicitud de correo, para que MSCorreos se encargue del envío real a AWS SES sin que ShrimpSoftServer tenga que conocer los detalles de AWS.

#### Criterios de Aceptación

1. THE MSCorreos SHALL exponer el endpoint `POST /api/v1/correos/enviar` que acepta una `SolicitudCorreo` en formato JSON.
2. WHEN MSCorreos recibe una `SolicitudCorreo` válida, THE MSCorreos SHALL enviar el correo a AWS_SES y retornar HTTP 200 con el identificador de mensaje SES.
3. IF la `SolicitudCorreo` recibida carece de campos obligatorios (destinatarios, asunto, tipoNotificacion, empresa), THEN THE MSCorreos SHALL retornar HTTP 400 con un mensaje de error descriptivo.
4. THE SolicitudCorreo SHALL contener los campos: `empresa` (String), `tipoNotificacion` (String), `destinatarios` (String, separados por `;`), `destinatariosCC` (String, opcional), `asunto` (String), `cuerpoTextoPlano` (String), `parametrosTemplate` (Map<String,Object>), `adjuntos` (List<AdjuntoDTO>), `claveAcceso` (String, opcional), `ruc` (String, opcional), `clave` (String, opcional).
5. WHEN MSCorreos recibe una `SolicitudCorreo` con adjuntos, THE MSCorreos SHALL incluir cada adjunto codificado en Base64 como parte MIME del correo enviado a AWS_SES.

---

### Requisito 2: Migración de la lógica AWS SES a MSCorreos

**User Story:** Como arquitecto del sistema, quiero que toda la integración con AWS SES resida únicamente en MSCorreos, para eliminar la dependencia de AWS SES en ShrimpSoftServer y centralizar la configuración de credenciales.

#### Criterios de Aceptación

1. THE MSCorreos SHALL contener toda la lógica de construcción y envío de mensajes MIME a AWS_SES, equivalente a la que actualmente existe en `UtilsMail.envioCorreoPersonalizadoAmazonSES`.
2. THE MSCorreos SHALL contener la lógica de envío de correos de error equivalente a `UtilsMail.envioErrorAmazonSES`.
3. THE MSCorreos SHALL implementar los métodos `esUnaEntidadVerificada`, `verificarEmail` y `listarEntidades` de AWS_SES, accesibles internamente.
4. WHEN ShrimpSoftServer complete la migración, THE ShrimpSoftServer SHALL eliminar la dependencia del SDK de AWS SES de su `pom.xml`.
5. THE MSCorreos SHALL establecer los tags de AWS_SES (`ows-tipo-notificacion`, `ows-empresa`, `ows-ruc`, `ows-clave`, `ows-clave-acceso`, etc.) a partir de los datos recibidos en la `SolicitudCorreo`, preservando el comportamiento actual de `UtilsMail.establecerTags`.

---

### Requisito 3: Sistema de templates HTML en MSCorreos

**User Story:** Como desarrollador, quiero que los templates HTML de los correos estén separados del código Java en archivos externos, para poder modificar el diseño de los correos sin recompilar la aplicación.

#### Criterios de Aceptación

1. THE MSCorreos SHALL almacenar los templates HTML como archivos en el directorio `resources/templates/correos/` del classpath, con un archivo por tipo de notificación.
2. WHEN MSCorreos recibe una `SolicitudCorreo`, THE MSCorreos SHALL seleccionar el template HTML correspondiente al `tipoNotificacion` indicado en la solicitud.
3. THE MSCorreos SHALL soportar templates con variables de sustitución (por ejemplo `{{nombreCliente}}`, `{{numeroFactura}}`) que se reemplacen con los valores del campo `parametrosTemplate` de la `SolicitudCorreo`.
4. WHERE una empresa tenga un template específico configurado, THE MSCorreos SHALL usar el template de empresa en lugar del template genérico para ese `tipoNotificacion`.
5. IF el template correspondiente al `tipoNotificacion` no existe en el sistema de archivos, THEN THE MSCorreos SHALL usar un template genérico de respaldo y registrar una advertencia en el log.
6. THE MSCorreos SHALL soportar header y footer HTML configurables por empresa, inyectados en el template antes del envío.

---

### Requisito 4: Configuración de AWS SES por empresa

**User Story:** Como administrador del sistema, quiero que cada empresa tenga su propia configuración de correo emisor en MSCorreos, para que los correos se envíen desde la identidad correcta de cada empresa en AWS SES.

#### Criterios de Aceptación

1. THE MSCorreos SHALL leer la configuración de envío por empresa (correo emisor, nombre emisor, configuration set de SES) desde la tabla `correos.cor_configuracion_empresa` o desde el archivo `application.properties`.
2. WHEN MSCorreos procesa una `SolicitudCorreo`, THE MSCorreos SHALL seleccionar la `ConfiguracionEmpresa` correspondiente al campo `empresa` de la solicitud.
3. IF no existe `ConfiguracionEmpresa` para la empresa indicada en la `SolicitudCorreo`, THEN THE MSCorreos SHALL usar la configuración por defecto y registrar una advertencia.
4. THE MSCorreos SHALL validar que el correo emisor de la `ConfiguracionEmpresa` sea una entidad verificada en AWS_SES antes de intentar el envío.

---

### Requisito 5: Manejo de errores

**User Story:** Como operador del sistema, quiero que MSCorreos registre los errores de envío, para tener visibilidad de los correos que no pudieron ser entregados.

#### Criterios de Aceptación

1. IF AWS_SES retorna un error durante el envío, THEN THE MSCorreos SHALL registrar el fallo en `correos.cor_notificaciones` con `n_tipo` = `'Error'` y `n_observacion` con el mensaje de error.
2. WHEN MSCorreos recibe un error de validación de AWS_SES (correo emisor no verificado, destinatario inválido), THE MSCorreos SHALL retornar HTTP 422 a ShrimpSoftServer con el detalle del error.
3. THE MSCorreos SHALL registrar en el log de aplicación cada envío fallido con la empresa, tipo de notificación y mensaje de error.

---

### Requisito 6: Registro de envíos en cor_notificaciones

**User Story:** Como auditor del sistema, quiero que cada correo enviado quede registrado en `correos.cor_notificaciones`, para tener trazabilidad completa de todos los envíos independientemente del módulo de origen.

#### Criterios de Aceptación

1. WHEN MSCorreos envía exitosamente un correo a AWS_SES, THE MSCorreos SHALL insertar un registro en `correos.cor_notificaciones` con `n_tipo` = `'Send'`, los datos de la `SolicitudCorreo` y el timestamp del envío.
2. WHEN MSCorreos recibe un webhook de AWS_SNS con evento `Delivery`, `Bounce`, `Complaint` u `Open`, THE MSCorreos SHALL insertar el registro correspondiente en `correos.cor_notificaciones` con el `n_tipo` apropiado.
3. THE cor_notificaciones SHALL incluir las columnas adicionales `n_asunto` (TEXT) y `n_modulo` (VARCHAR) para soportar la trazabilidad completa de los envíos migrados.
4. THE MSCorreos SHALL poblar `n_tipo_notificacion` con el valor descriptivo del `TipoNotificacion` correspondiente (por ejemplo `'VENTAS ELECTRÓNICAS EMITIDAS'`).
5. WHEN MSCorreos registra un envío, THE MSCorreos SHALL poblar `n_empresa`, `n_ruc` y `n_clave` con los valores recibidos en la `SolicitudCorreo`.

---

### Requisito 7: Adaptación de ShrimpSoftServer — Cliente HTTP

**User Story:** Como desarrollador de ShrimpSoftServer, quiero que `EnviarCorreoServiceImpl` llame a MSCorreos vía HTTP en lugar de llamar directamente a AWS SES, para que ShrimpSoftServer no tenga dependencias de infraestructura de correo.

#### Criterios de Aceptación

1. THE EnviarCorreoServiceImpl SHALL construir una `SolicitudCorreo` con todos los datos necesarios y enviarla mediante HTTP POST al endpoint `POST /api/v1/correos/enviar` de MSCorreos.
2. THE Cliente_HTTP SHALL leer la URL base de MSCorreos desde la propiedad de configuración `msCorreos.url` en `application.properties`.
3. IF MSCorreos retorna HTTP 4xx o 5xx, THEN THE EnviarCorreoServiceImpl SHALL lanzar una excepción del tipo `GeneralException` con el mensaje de error recibido.
4. THE EnviarCorreoServiceImpl SHALL codificar los adjuntos en Base64 antes de incluirlos en la `SolicitudCorreo`.
5. THE EnviarCorreoService SHALL mantener la misma firma de métodos públicos que la interfaz actual, de modo que los controladores REST de ShrimpSoftServer no requieran modificaciones.
6. WHEN ShrimpSoftServer complete la migración, THE ShrimpSoftServer SHALL eliminar todas las llamadas directas a `UtilsMail` de `EnviarCorreoServiceImpl`.

---

### Requisito 8: Nuevos tipos de notificación

**User Story:** Como desarrollador, quiero que el enum `TipoNotificacion` de MSCorreos incluya todos los tipos de correo que actualmente maneja ShrimpSoftServer, para que el sistema de registro y templates cubra todos los casos de uso.

#### Criterios de Aceptación

1. THE TipoNotificacion SHALL incluir los tipos adicionales: `NOTIFICAR_PAGO_PROVEEDOR`, `NOTIFICAR_ANTICIPO_PROVEEDOR`, `NOTIFICAR_BENEFICIO_XIII`, `NOTIFICAR_BENEFICIO_XIV`, `NOTIFICAR_BENEFICIO_UTILIDADES`, `NOTIFICAR_ERROR_SISTEMA`, `NOTIFICAR_TICKET_SOPORTE`, `NOTIFICAR_DOCUMENTO_NO_AUTORIZADO`, `NOTIFICAR_PROVEEDOR_IMB`, `NOTIFICAR_ANULACION_VENTA`, `NOTIFICAR_ANULACION_RETENCION_COMPRA`, `NOTIFICAR_ORDEN_COMPRA_REGISTRADOR`.
2. WHEN MSCorreos recibe una `SolicitudCorreo` con un `tipoNotificacion` no reconocido, THE MSCorreos SHALL retornar HTTP 400 con el mensaje `'Tipo de notificación no reconocido: {valor}'`.
3. THE TipoNotificacion SHALL exponer un método estático `fromCodigo(String codigo)` que retorne el enum correspondiente o lance `IllegalArgumentException` si el código no existe.

---

### Requisito 9: Migración de datos históricos

**User Story:** Como administrador de base de datos, quiero un script SQL que migre los registros históricos de las 9 tablas de notificaciones actuales a `correos.cor_notificaciones`, para consolidar el historial completo en una sola tabla.

#### Criterios de Aceptación

1. THE Script_Migracion SHALL migrar los registros de las siguientes tablas a `correos.cor_notificaciones`:
   - `anexo.anx_venta_electronica_notificaciones`
   - `anexo.anx_compra_electronica_notificaciones`
   - `anexo.anx_guia_remision_electronica_notificaciones`
   - `anexo.anx_liquidacion_compras_electronica_notificaciones`
   - `cartera.car_pagos_notificaciones`
   - `cartera.car_pagos_anticipos_notificaciones`
   - `recursoshumanos.rh_rol_pago_notificaciones`
   - `inventario.inv_pedidos_orden_compra_notificaciones`
   - `inventario.inv_cliente_notificaciones`
2. THE Script_Migracion SHALL mapear el campo de tipo de cada tabla origen al valor de `n_tipo_notificacion` correspondiente en `cor_notificaciones`.
3. THE Script_Migracion SHALL ejecutarse dentro de una transacción, de modo que IF ocurre un error en cualquier tabla, THEN THE Script_Migracion SHALL hacer rollback de toda la migración.
4. THE Script_Migracion SHALL poblar `n_modulo` con el nombre del esquema y tabla de origen (por ejemplo `'anexo.anx_venta_electronica_notificaciones'`) para mantener trazabilidad del origen del dato.
5. WHEN el Script_Migracion finaliza exitosamente, THE Script_Migracion SHALL imprimir el número de registros migrados por tabla de origen.
6. THE Script_Migracion SHALL ser idempotente: IF se ejecuta más de una vez, THEN THE Script_Migracion SHALL omitir los registros ya migrados sin generar duplicados, usando una clave de deduplicación basada en `n_modulo` + identificador original.

---

### Requisito 10: Seguridad del endpoint de MSCorreos

**User Story:** Como arquitecto de seguridad, quiero que el endpoint de envío de correos de MSCorreos solo sea accesible desde ShrimpSoftServer, para evitar que actores no autorizados envíen correos a través del sistema.

#### Criterios de Aceptación

1. THE MSCorreos SHALL requerir un token de autenticación estático (API Key) en el header `X-API-Key` para todas las solicitudes al endpoint `POST /api/v1/correos/enviar`.
2. IF una solicitud al endpoint de envío no incluye el header `X-API-Key` o incluye un valor incorrecto, THEN THE MSCorreos SHALL retornar HTTP 401 sin procesar la solicitud.
3. THE MSCorreos SHALL leer el valor esperado del API Key desde la propiedad `msCorreos.apiKey` en `application.properties`, sin hardcodear el valor en el código fuente.
4. THE Cliente_HTTP en ShrimpSoftServer SHALL incluir el header `X-API-Key` con el valor configurado en `msCorreos.apiKey` en cada solicitud a MSCorreos.
