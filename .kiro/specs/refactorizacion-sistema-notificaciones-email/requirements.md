# Documento de Requisitos: Consolidación de Tablas de Notificaciones

## Introducción

El sistema actual de ShrimpSoftServer envía correos electrónicos usando `UtilsMail.java` y registra las notificaciones en **12+ tablas dispersas** distribuidas en diferentes esquemas de base de datos:

- `anexo.anx_venta_electronica_notificaciones`
- `anexo.anx_compra_electronica_notificaciones`
- `anexo.anx_guia_remision_electronica_notificaciones`
- `inventario.inv_pedidos_orden_compra_notificaciones`
- `inventario.inv_pedidos_orden_compra_anulada_notificaciones`
- `inventario.inv_cliente_notificaciones`
- `inventario.inv_imb_notificaciones`
- `recursoshumanos.rh_rol_pago_notificaciones`
- `cartera.car_pagos_anticipos_notificaciones`
- `contabilidad.con_verificacion_errores_notificaciones`
- `sistemaweb.sis_notificaciones_errores_contabilidad`
- `sistemaweb.sis_notificaciones_errores_inventario`

Esta dispersión dificulta consultas globales, mantenimiento, auditoría y gestión de lista negra.

**Objetivo**: Consolidar todas las notificaciones en una única tabla `correos.cor_notificaciones` dentro del microservicio MSCorreos. ShrimpSoftServer continuará usando `UtilsMail.java` para enviar correos, pero MSCorreos será responsable de registrar eventos de tracking y gestionar la lista negra centralizada.

## Glosario

- **MSCorreos**: Microservicio responsable de recibir eventos de tracking de Amazon SNS y gestionar la lista negra centralizada
- **ShrimpSoftServer**: Servidor principal que envía correos usando UtilsMail.java y registra en tablas dispersas (sistema actual)
- **UtilsMail**: Clase Java en ShrimpSoftServer que envía correos mediante Amazon SES
- **Tabla_Dispersa**: Tabla de notificaciones específica de un módulo (ej: anx_venta_electronica_notificaciones)
- **Tabla_Unificada**: Tabla correos.cor_notificaciones que consolida todas las notificaciones
- **Lista_Negra**: Tabla correos.cor_lista_negra que contiene correos bloqueados para evitar envíos
- **Tracking_Event**: Evento de seguimiento de correo enviado por Amazon SNS (Send, Delivery, Open, Bounce, Complaint)
- **Hard_Bounce**: Rebote permanente (dirección no existe, dominio inválido)
- **Soft_Bounce**: Rebote temporal (buzón lleno, servidor no disponible temporalmente)
- **Complaint**: Queja de spam reportada por el destinatario
- **Configuration_Set**: Configuración de Amazon SES para tracking de eventos
- **Message_Tag**: Etiqueta de metadatos en mensajes SES (ows-empresa, ows-ruc, ows-clave, etc.)
- **Tipo_Notificacion**: Enum que identifica el tipo de notificación (NOTIFICAR_VENTA_ELECTRONICA_EMITIDA, NOTIFICAR_PROVEEDOR_ORDEN_COMPRA, etc.)
- **Clave_Compuesta**: Identificador único formado por periodo_motivo_numero o sector_motivo_numero
- **Campo_Comun**: Campo presente en todas las tablas dispersas (destinatario, fecha, tipo, observacion, informe, empresa)
- **Campo_Especifico**: Campo único de una tabla dispersa (periodo, motivo, numero, sector, contable, cli_codigo)

## Requisitos

### Requisito 1: Análisis del Sistema Actual

**User Story:** Como arquitecto del sistema, quiero documentar cómo funciona el sistema actual de notificaciones dispersas, para entender qué datos debo consolidar.

#### Acceptance Criteria

1. THE Sistema SHALL identificar todas las tablas dispersas de notificaciones en los esquemas: anexo, inventario, recursoshumanos, cartera, contabilidad, sistemaweb
2. THE Sistema SHALL documentar los campos comunes en todas las tablas: secuencial, destinatario, fecha, tipo, observacion, informe, empresa
3. THE Sistema SHALL documentar los campos específicos por módulo: periodo, motivo, numero (comprobantes), sector (órdenes), contable (roles), cli_codigo (clientes)
4. THE Sistema SHALL identificar los 12 tipos de notificación del enum TipoNotificacion
5. THE Sistema SHALL documentar cómo UtilsMail.java establece tags de SES: ows-tipo-notificacion, ows-empresa, ows-ruc, ows-clave, ows-clave-acceso

### Requisito 2: Diseño de Tabla Unificada

**User Story:** Como arquitecto de datos, quiero diseñar una tabla unificada que consolide todas las tablas dispersas, para eliminar la duplicación de esquemas.

#### Acceptance Criteria

1. THE Tabla_Unificada SHALL contener los campos: n_secuencial, n_destinatario, n_fecha, n_tipo, n_observacion, n_informe, n_empresa, n_ruc, n_clave, n_tipo_notificacion
2. THE Campo n_clave SHALL almacenar la clave compuesta (periodo_motivo_numero o sector_motivo_numero o contable)
3. THE Campo n_informe SHALL almacenar el JSON completo del evento de tracking de Amazon SNS
4. THE Campo n_tipo_notificacion SHALL almacenar el valor del enum TipoNotificacion
5. THE Tabla_Unificada SHALL tener índices en: n_empresa, n_tipo_notificacion, n_fecha, n_destinatario, n_ruc, n_clave, n_tipo
6. THE Tabla_Unificada SHALL tener un índice compuesto en (n_empresa, n_tipo_notificacion, n_fecha)

### Requisito 3: Mapeo de Campos Específicos a Tabla Unificada

**User Story:** Como desarrollador, quiero mapear los campos específicos de cada tabla dispersa a la tabla unificada, para no perder información durante la consolidación.

#### Acceptance Criteria

1. WHEN se consolida una notificación de comprobante electrónico, THE Sistema SHALL mapear periodo, motivo, numero a n_clave como "periodo_motivo_numero"
2. WHEN se consolida una notificación de orden de compra, THE Sistema SHALL mapear sector, motivo, numero a n_clave como "sector_motivo_numero"
3. WHEN se consolida una notificación de rol de pagos, THE Sistema SHALL mapear contable a n_clave
4. WHEN se consolida una notificación de cliente, THE Sistema SHALL mapear cli_codigo a n_clave y asunto a n_observacion
5. WHEN se consolida una notificación de IMB, THE Sistema SHALL mapear proveedor a n_observacion
6. THE Sistema SHALL preservar todos los campos específicos en el JSON del campo n_informe

### Requisito 4: Migración de Datos Históricos

**User Story:** Como administrador del sistema, quiero migrar los datos históricos de las 12+ tablas dispersas a la tabla unificada, para no perder información histórica.

#### Acceptance Criteria

1. THE Sistema SHALL proporcionar un script SQL que migre datos de anexo.anx_venta_electronica_notificaciones a correos.cor_notificaciones
2. THE Sistema SHALL proporcionar un script SQL que migre datos de anexo.anx_compra_electronica_notificaciones a correos.cor_notificaciones
3. THE Sistema SHALL proporcionar un script SQL que migre datos de anexo.anx_guia_remision_electronica_notificaciones a correos.cor_notificaciones
4. THE Sistema SHALL proporcionar un script SQL que migre datos de inventario.inv_pedidos_orden_compra_notificaciones a correos.cor_notificaciones
5. THE Sistema SHALL proporcionar un script SQL que migre datos de inventario.inv_pedidos_orden_compra_anulada_notificaciones a correos.cor_notificaciones
6. THE Sistema SHALL proporcionar un script SQL que migre datos de inventario.inv_cliente_notificaciones a correos.cor_notificaciones
7. THE Sistema SHALL proporcionar un script SQL que migre datos de inventario.inv_imb_notificaciones a correos.cor_notificaciones
8. THE Sistema SHALL proporcionar un script SQL que migre datos de recursoshumanos.rh_rol_pago_notificaciones a correos.cor_notificaciones
9. THE Sistema SHALL proporcionar un script SQL que migre datos de cartera.car_pagos_anticipos_notificaciones a correos.cor_notificaciones
10. THE Sistema SHALL proporcionar un script SQL que migre datos de contabilidad.con_verificacion_errores_notificaciones a correos.cor_notificaciones
11. WHEN se ejecuta la migración, THE Script SHALL marcar registros migrados agregando "[MIGRADO_DE: nombre_tabla]" en n_observacion
12. THE Script SHALL generar un reporte con: total de registros migrados por tabla, registros con errores, tiempo de ejecución

### Requisito 5: Recepción de Eventos de Tracking desde Amazon SNS

**User Story:** Como administrador, quiero que MSCorreos reciba eventos de tracking de Amazon SNS, para registrar el estado de los correos en la tabla unificada.

#### Acceptance Criteria

1. THE MSCorreos SHALL exponer un endpoint POST /api/v1/sns/tracking para recibir notificaciones de Amazon SNS
2. WHEN Amazon SNS envía una notificación de suscripción, THE MSCorreos SHALL confirmar la suscripción automáticamente
3. WHEN se recibe un Tracking_Event de tipo "Send", THE MSCorreos SHALL registrar en Tabla_Unificada con n_tipo="Send"
4. WHEN se recibe un Tracking_Event de tipo "Delivery", THE MSCorreos SHALL registrar en Tabla_Unificada con n_tipo="Delivery"
5. WHEN se recibe un Tracking_Event de tipo "Open", THE MSCorreos SHALL registrar en Tabla_Unificada con n_tipo="Open"
6. WHEN se recibe un Tracking_Event de tipo "Click", THE MSCorreos SHALL registrar en Tabla_Unificada con n_tipo="Click"
7. WHEN se recibe un Tracking_Event de tipo "Bounce", THE MSCorreos SHALL registrar en Tabla_Unificada con n_tipo="Bounce" o "BouncePermanent" o "BounceTransient" según bounceType
8. WHEN se recibe un Tracking_Event de tipo "Complaint", THE MSCorreos SHALL registrar en Tabla_Unificada con n_tipo="Complaint"
9. THE MSCorreos SHALL extraer de los Message_Tag del evento: ows-empresa, ows-ruc, ows-clave, ows-tipo-notificacion
10. THE MSCorreos SHALL almacenar el JSON completo del evento SNS en el campo n_informe

### Requisito 6: Gestión de Lista Negra Centralizada

**User Story:** Como administrador del sistema, quiero gestionar una lista negra centralizada de correos bloqueados, para evitar enviar correos a direcciones problemáticas y prevenir multas de AWS SES.

#### Acceptance Criteria

1. THE Sistema SHALL mantener una tabla correos.cor_lista_negra con campos: id, email, motivo, fecha_registro, tipo_bloqueo, activo, contador_soft_bounce, ultimo_soft_bounce
2. WHEN se recibe un Tracking_Event de tipo "Bounce" con bounceType="Permanent", THE MSCorreos SHALL agregar el email a la lista negra con tipo_bloqueo="HARD_BOUNCE"
3. WHEN se recibe un Tracking_Event de tipo "Complaint", THE MSCorreos SHALL agregar el email a la lista negra con tipo_bloqueo="COMPLAINT"
4. WHEN un email genera un Soft Bounce, THE MSCorreos SHALL incrementar contador_soft_bounce y actualizar ultimo_soft_bounce
5. WHEN un email alcanza 3 Soft Bounce en 30 días, THE MSCorreos SHALL agregarlo a la lista negra con tipo_bloqueo="SOFT_BOUNCE_REPETIDO"
6. THE Sistema SHALL permitir agregar correos manualmente a la lista negra con tipo_bloqueo="MANUAL"
7. THE Sistema SHALL mantener un historial de cambios en correos.cor_lista_negra_historial con: id, email, accion (AGREGAR/REMOVER), motivo, usuario, fecha

### Requisito 7: API REST para Consulta de Notificaciones

**User Story:** Como desarrollador, quiero consultar el estado de las notificaciones mediante una API REST, para integrar con otros sistemas y generar reportes.

#### Acceptance Criteria

1. THE MSCorreos SHALL exponer un endpoint GET /api/v1/notificaciones para listar notificaciones
2. THE Endpoint SHALL soportar filtros por: empresa, ruc, tipo_notificacion, fecha_inicio, fecha_fin, destinatario, tipo
3. THE Endpoint SHALL soportar paginación con parámetros: page, size (máximo 100 registros por página)
4. THE Endpoint SHALL retornar respuestas en formato JSON con: n_secuencial, n_destinatario, n_fecha, n_tipo, n_tipo_notificacion, n_empresa, n_ruc, n_clave
5. THE MSCorreos SHALL exponer un endpoint GET /api/v1/notificaciones/{id} para obtener detalles de una notificación específica incluyendo n_informe completo
6. THE MSCorreos SHALL exponer un endpoint GET /api/v1/notificaciones/estadisticas para obtener métricas agregadas por empresa, tipo_notificacion y tipo de evento

### Requisito 8: API REST para Gestión de Lista Negra

**User Story:** Como administrador, quiero gestionar la lista negra mediante una API REST, para bloquear o desbloquear correos manualmente.

#### Acceptance Criteria

1. THE MSCorreos SHALL exponer un endpoint POST /api/v1/lista-negra para agregar correos manualmente con tipo_bloqueo="MANUAL"
2. THE MSCorreos SHALL exponer un endpoint DELETE /api/v1/lista-negra/{email} para desactivar un correo de la lista negra (activo=false)
3. THE MSCorreos SHALL exponer un endpoint GET /api/v1/lista-negra para consultar correos bloqueados con filtros por: tipo_bloqueo, activo, fecha_desde, fecha_hasta
4. THE MSCorreos SHALL exponer un endpoint GET /api/v1/lista-negra/{email} para consultar el estado de un correo específico
5. THE MSCorreos SHALL exponer un endpoint GET /api/v1/lista-negra/{email}/historial para consultar el historial de cambios de un correo
6. WHEN se agrega o remueve un correo de la lista negra, THE MSCorreos SHALL registrar la acción en cor_lista_negra_historial

### Requisito 9: Validación de Integridad de Datos

**User Story:** Como administrador, quiero validar que los datos migrados sean íntegros, para garantizar que no se perdió información durante la consolidación.

#### Acceptance Criteria

1. THE Script_Migracion SHALL generar un reporte de validación que compare el total de registros en tablas dispersas vs tabla unificada
2. THE Script_Migracion SHALL validar que todos los campos comunes se migraron correctamente (destinatario, fecha, tipo, observacion, informe, empresa)
3. THE Script_Migracion SHALL validar que los campos específicos se preservaron en n_clave o n_informe
4. THE Script_Migracion SHALL identificar registros con datos faltantes o inconsistentes
5. THE Script_Migracion SHALL generar un archivo CSV con registros que fallaron la migración para revisión manual

### Requisito 10: Estrategia de Transición

**User Story:** Como arquitecto del sistema, quiero definir una estrategia de transición gradual, para minimizar el riesgo durante la migración.

#### Acceptance Criteria

1. DURING Fase 1 (Preparación), THE Sistema SHALL crear la tabla unificada y configurar MSCorreos para recibir eventos SNS
2. DURING Fase 2 (Migración de Datos), THE Sistema SHALL ejecutar scripts de migración de datos históricos en ambiente de pruebas
3. DURING Fase 3 (Doble Escritura), THE ShrimpSoftServer SHALL continuar escribiendo en tablas dispersas Y MSCorreos registrará eventos SNS en tabla unificada
4. DURING Fase 4 (Validación), THE Sistema SHALL comparar datos entre tablas dispersas y tabla unificada para detectar discrepancias
5. DURING Fase 5 (Migración Completa), THE Sistema SHALL ejecutar migración de datos históricos en producción
6. DURING Fase 6 (Solo Tabla Unificada), THE Sistema SHALL desactivar escritura en tablas dispersas y configurarlas en modo solo lectura
7. DURING Fase 7 (Limpieza), THE Sistema SHALL archivar tablas dispersas después de 90 días de validación

### Requisito 11: Consultas de Compatibilidad

**User Story:** Como desarrollador, quiero crear vistas SQL que emulen las tablas dispersas, para mantener compatibilidad con consultas existentes durante la transición.

#### Acceptance Criteria

1. THE Sistema SHALL crear una vista anexo.anx_venta_electronica_notificaciones que proyecte datos de correos.cor_notificaciones filtrados por tipo_notificacion
2. THE Sistema SHALL crear una vista anexo.anx_compra_electronica_notificaciones que proyecte datos de correos.cor_notificaciones
3. THE Sistema SHALL crear una vista anexo.anx_guia_remision_electronica_notificaciones que proyecte datos de correos.cor_notificaciones
4. THE Sistema SHALL crear una vista inventario.inv_pedidos_orden_compra_notificaciones que proyecte datos de correos.cor_notificaciones
5. THE Sistema SHALL crear vistas para todas las tablas dispersas restantes
6. THE Vistas SHALL extraer campos específicos desde n_clave usando funciones SQL (split_part)
7. THE Vistas SHALL mantener los nombres de columnas originales para compatibilidad

### Requisito 12: Monitoreo y Observabilidad

**User Story:** Como administrador del sistema, quiero monitorear el estado del sistema de notificaciones, para detectar y resolver problemas rápidamente.

#### Acceptance Criteria

1. THE MSCorreos SHALL enviar logs estructurados con nivel INFO, WARN y ERROR
2. THE MSCorreos SHALL incluir en cada log: timestamp, nivel, mensaje, empresa, tipo_notificacion, destinatario
3. THE MSCorreos SHALL exponer un endpoint GET /health que retorne HTTP 200 cuando el servicio está operativo
4. THE MSCorreos SHALL exponer un endpoint GET /metrics que retorne métricas en formato Prometheus
5. THE Sistema SHALL registrar métricas de: total_notificaciones_registradas, notificaciones_por_tipo, correos_en_lista_negra, eventos_sns_recibidos
6. THE Sistema SHALL crear alarmas cuando la lista negra supere 1000 correos
7. THE Sistema SHALL crear alarmas cuando la tasa de Bounce supere el 5% en 24 horas

### Requisito 13: Seguridad y Autenticación

**User Story:** Como administrador de seguridad, quiero que las APIs estén protegidas, para evitar accesos no autorizados.

#### Acceptance Criteria

1. THE MSCorreos SHALL requerir autenticación mediante API Key en el header X-API-Key para endpoints de consulta
2. THE MSCorreos SHALL validar la firma de mensajes SNS para verificar que provienen de Amazon
3. THE MSCorreos SHALL validar y sanitizar todos los inputs antes de procesarlos
4. THE MSCorreos SHALL no registrar en logs información sensible como claves de acceso completas
5. WHEN una solicitud no incluye API Key válida, THE MSCorreos SHALL retornar HTTP 401 Unauthorized
6. WHEN se detecta un mensaje SNS con firma inválida, THE MSCorreos SHALL rechazar el mensaje y registrar un evento de seguridad

## Requisitos No Funcionales

### Requisito NF1: Performance

**User Story:** Como usuario del sistema, quiero que las consultas sean rápidas, para obtener información de notificaciones sin demoras.

#### Acceptance Criteria

1. THE API REST SHALL responder consultas en menos de 500 ms en el percentil 95
2. THE Sistema SHALL procesar un evento SNS y registrarlo en la tabla unificada en menos de 200 ms
3. THE Sistema SHALL soportar al menos 100 consultas concurrentes sin degradación
4. THE Consultas con filtros por empresa y tipo_notificacion SHALL usar índices compuestos para optimización

### Requisito NF2: Disponibilidad

**User Story:** Como administrador del sistema, quiero que MSCorreos esté disponible 24/7, para no perder eventos de tracking.

#### Acceptance Criteria

1. THE MSCorreos SHALL tener una disponibilidad del 99.5% mensual
2. THE Sistema SHALL implementar health checks que verifiquen conectividad a base de datos
3. WHEN el health check falla 3 veces consecutivas, THE Sistema SHALL reiniciar automáticamente el servicio
4. THE Sistema SHALL mantener al menos 2 instancias activas para alta disponibilidad

### Requisito NF3: Mantenibilidad

**User Story:** Como desarrollador, quiero que el código sea mantenible, para facilitar futuras modificaciones.

#### Acceptance Criteria

1. THE MSCorreos SHALL implementar Clean Architecture con capas: Domain, Application, Infrastructure, Presentation
2. THE Código SHALL tener una cobertura de pruebas unitarias del 80% mínimo
3. THE Código SHALL seguir los principios SOLID
4. THE Sistema SHALL usar inyección de dependencias mediante Spring Framework
5. THE Código SHALL estar documentado con Javadoc en clases y métodos públicos

### Requisito NF4: Auditoría

**User Story:** Como auditor, quiero tener trazabilidad completa de todas las notificaciones, para cumplir con requisitos regulatorios.

#### Acceptance Criteria

1. THE Sistema SHALL registrar cada evento de tracking con timestamp preciso
2. THE Sistema SHALL preservar el JSON completo del evento SNS en el campo n_informe
3. THE Sistema SHALL mantener registros de notificaciones por al menos 7 años
4. THE Sistema SHALL registrar todos los cambios en la lista negra en cor_lista_negra_historial
5. THE Sistema SHALL permitir exportar registros de notificaciones en formato CSV

### Requisito NF5: Compatibilidad

**User Story:** Como desarrollador, quiero que el sistema sea compatible con la infraestructura existente, para facilitar la integración.

#### Acceptance Criteria

1. THE MSCorreos SHALL ejecutarse en Java 11 o superior
2. THE MSCorreos SHALL usar Spring Boot 2.7 o superior
3. THE MSCorreos SHALL conectarse a PostgreSQL 12 o superior
4. THE MSCorreos SHALL ser compatible con AWS SDK for Java 2.x
5. THE Sistema SHALL mantener compatibilidad con el formato actual de tags de SES (ows-*)

## Estrategia de Migración

### Fase 1: Preparación (Semana 1-2)

1. Crear la tabla correos.cor_notificaciones en el esquema de base de datos
2. Crear la tabla correos.cor_lista_negra para gestión de correos bloqueados
3. Crear la tabla correos.cor_lista_negra_historial para auditoría de cambios
4. Configurar Configuration Set en Amazon SES para tracking de eventos
5. Configurar topic SNS para recibir eventos de tracking
6. Implementar MSCorreos con endpoint SNS para recibir eventos
7. Migrar correos problemáticos existentes a la lista negra inicial

### Fase 2: Desarrollo de MSCorreos (Semana 3-4)

1. Implementar MSCorreos con Clean Architecture (Domain, Application, Infrastructure, Presentation)
2. Desarrollar endpoint POST /api/v1/sns/tracking para recibir eventos SNS
3. Implementar lógica de procesamiento de eventos de tracking
4. Desarrollar lógica de gestión automática de lista negra
5. Implementar API REST para consulta de notificaciones
6. Implementar API REST para gestión de lista negra
7. Desarrollar pruebas unitarias e integración

### Fase 3: Migración de Datos Históricos (Semana 5-6)

1. Desarrollar scripts SQL de migración para cada tabla dispersa
2. Ejecutar scripts de migración en ambiente de pruebas
3. Validar integridad de datos migrados
4. Crear vistas SQL de compatibilidad para emular tablas dispersas
5. Generar reportes de validación

### Fase 4: Despliegue y Validación (Semana 7-8)

1. Desplegar MSCorreos en ambiente de producción
2. Configurar suscripción SNS a topic de tracking
3. Validar que eventos SNS se registran correctamente en tabla unificada
4. Ejecutar scripts de migración de datos históricos en producción
5. Monitorear sistema durante 2 semanas

### Fase 5: Transición Completa (Semana 9-10)

1. Validar que todas las notificaciones nuevas se registran en tabla unificada
2. Comparar datos entre tablas dispersas y tabla unificada
3. Configurar tablas dispersas en modo solo lectura
4. Documentar la nueva arquitectura
5. Capacitar al equipo en el nuevo sistema

### Fase 6: Limpieza (Semana 11-12)

1. Archivar tablas dispersas después de 90 días de validación
2. Eliminar vistas de compatibilidad si ya no son necesarias
3. Actualizar documentación técnica
4. Establecer procedimientos de operación y mantenimiento

## Restricciones Técnicas

1. SOLO MSCorreos será modificado, NO ShrimpSoftServer ni otros repositorios
2. ShrimpSoftServer continuará usando UtilsMail.java para enviar correos mediante Amazon SES
3. El sistema debe mantener compatibilidad con el esquema de tags actual de Amazon SES (ows-*)
4. El sistema debe soportar el formato actual de eventos de Amazon SNS sin cambios
5. El sistema debe mantener el formato de Clave_Acceso de 49 dígitos para comprobantes electrónicos
6. El sistema debe usar PostgreSQL como base de datos por estándar corporativo
7. El sistema debe implementarse en Java con Spring Boot por estándar corporativo
8. La tabla unificada ya existe (Task 1 completado): correos.cor_notificaciones

## Criterios de Aceptación del Proyecto

1. La tabla correos.cor_notificaciones consolida todas las notificaciones del sistema
2. MSCorreos recibe eventos de tracking desde Amazon SNS y los registra en la tabla unificada
3. MSCorreos gestiona automáticamente la lista negra basándose en eventos de Bounce y Complaint
4. El sistema registra todos los eventos de tracking (Send, Delivery, Open, Bounce, Complaint)
5. La API REST permite consultar notificaciones con filtros por empresa, tipo, fecha, destinatario
6. La API REST permite gestionar la lista negra (agregar, remover, consultar)
7. Los datos históricos de las 12+ tablas dispersas se migraron exitosamente sin pérdida de información
8. Las vistas SQL de compatibilidad permiten consultar datos usando los nombres de tablas antiguas
9. El sistema cumple con los requisitos no funcionales de performance, disponibilidad y seguridad
10. La documentación técnica está completa y el equipo está capacitado
11. No se envían correos a direcciones en lista negra (validación en ShrimpSoftServer o MSCorreos)
12. El sistema está en producción y operando correctamente durante al menos 30 días
