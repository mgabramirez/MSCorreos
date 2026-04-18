# Documento de Requisitos: Refactorización del Sistema de Notificaciones por Correo Electrónico

## Introducción

El sistema actual de notificaciones por correo electrónico de MSCorreos presenta múltiples problemas de arquitectura: cada módulo gestiona su propia tabla de notificaciones, existe alto acoplamiento con ShrimpSoftServer, no hay sistema de colas robusto, y el manejo de fallos es limitado. Esta refactorización busca centralizar el sistema en una única tabla global (correos.cor_notificaciones), implementar una arquitectura basada en eventos usando servicios AWS (SQS, Lambda/ECS, SES, SNS), desacoplar completamente MSCorreos como microservicio independiente, y garantizar resiliencia, escalabilidad y observabilidad.

## Glosario

- **MSCorreos**: Microservicio responsable de gestionar el envío y tracking de notificaciones por correo electrónico
- **ShrimpSoftServer**: Servidor principal del sistema que actualmente gestiona múltiples módulos (cartera, inventario, RRHH, etc.)
- **Amazon_SES**: Amazon Simple Email Service, servicio de AWS para envío de correos electrónicos
- **Amazon_SQS**: Amazon Simple Queue Service, servicio de cola de mensajes de AWS
- **Amazon_SNS**: Amazon Simple Notification Service, servicio de notificaciones pub/sub de AWS
- **Dead_Letter_Queue**: Cola especial para mensajes que no pudieron ser procesados después de múltiples reintentos
- **Circuit_Breaker**: Patrón de diseño para prevenir fallos en cascada
- **Tabla_Global_Notificaciones**: Tabla correos.cor_notificaciones que centraliza todas las notificaciones
- **Lista_Negra**: Tabla correos.cor_lista_negra que contiene correos electrónicos bloqueados para evitar envíos y multas de AWS
- **Evento_Notificacion**: Mensaje que representa una solicitud de envío de correo electrónico
- **Productor**: Componente que genera eventos de notificación (módulos de ShrimpSoftServer)
- **Consumidor**: Componente que procesa eventos de notificación y envía correos (MSCorreos)
- **Tracking_Event**: Evento de seguimiento de correo (Send, Delivery, Open, Bounce, Complaint)
- **Hard_Bounce**: Rebote permanente de correo (dirección no existe, dominio inválido)
- **Soft_Bounce**: Rebote temporal de correo (buzón lleno, servidor temporalmente no disponible)
- **Complaint**: Queja de spam reportada por el destinatario
- **Comprobante_Electronico**: Documento tributario electrónico (factura, nota de crédito, guía de remisión, etc.)
- **Orden_Compra**: Documento sin validez tributaria para solicitar productos a proveedores
- **Rol_Pagos**: Documento de nómina para empleados
- **Clave_Acceso**: Identificador único de 49 dígitos para comprobantes electrónicos
- **CloudWatch**: Servicio de AWS para logs, métricas y monitoreo
- **Idempotencia**: Propiedad que garantiza que procesar el mismo mensaje múltiples veces produce el mismo resultado

## Requisitos

### Requisito 1: Centralización de Notificaciones

**User Story:** Como arquitecto del sistema, quiero centralizar todas las notificaciones en una única tabla global, para eliminar la duplicación de datos y simplificar el mantenimiento.

#### Acceptance Criteria

1. THE Sistema SHALL almacenar todas las notificaciones de correo en la tabla correos.cor_notificaciones
2. WHEN un módulo necesita enviar una notificación, THE Productor SHALL publicar un Evento_Notificacion en Amazon_SQS
3. THE Tabla_Global_Notificaciones SHALL contener los campos: n_secuencial, n_destinatario, n_fecha, n_tipo, n_observacion, n_informe, n_empresa, n_ruc, n_clave, n_tipo_notificacion
4. THE Sistema SHALL eliminar progresivamente las tablas de notificaciones por módulo (cartera.car_pagos_notificaciones y similares)
5. WHEN se migran datos históricos, THE Sistema SHALL preservar toda la información de las tablas antiguas en la Tabla_Global_Notificaciones

### Requisito 2: Arquitectura Basada en Eventos

**User Story:** Como desarrollador, quiero implementar una arquitectura basada en eventos, para desacoplar los productores de los consumidores y mejorar la escalabilidad.

#### Acceptance Criteria

1. THE Productor SHALL publicar Evento_Notificacion en una cola Amazon_SQS estándar
2. THE Evento_Notificacion SHALL contener: destinatarios, asunto, cuerpo HTML, cuerpo texto plano, adjuntos (referencias S3), tipo de notificación, empresa, RUC, clave, metadatos adicionales
3. THE Consumidor SHALL suscribirse a la cola Amazon_SQS y procesar mensajes de forma asíncrona
4. WHEN un mensaje no puede ser procesado después de 3 reintentos, THE Sistema SHALL mover el mensaje a la Dead_Letter_Queue
5. THE Sistema SHALL configurar un tiempo de visibilidad de 30 segundos para mensajes en la cola
6. THE Sistema SHALL garantizar Idempotencia en el procesamiento de mensajes mediante identificadores únicos

### Requisito 3: Envío de Correos mediante Amazon SES

**User Story:** Como usuario del sistema, quiero que los correos se envíen de forma confiable mediante Amazon SES, para garantizar alta tasa de entrega.

#### Acceptance Criteria

1. WHEN el Consumidor procesa un Evento_Notificacion, THE MSCorreos SHALL consultar la lista negra ANTES de enviar el correo
2. WHEN el Consumidor procesa un Evento_Notificacion, THE MSCorreos SHALL enviar el correo usando Amazon_SES
3. THE MSCorreos SHALL configurar tags de Amazon_SES con: ows-tipo-notificacion, ows-empresa, ows-ruc, ows-clave, ows-clave-acceso (si aplica)
4. THE MSCorreos SHALL configurar un Configuration Set en Amazon_SES para tracking de eventos
5. WHEN el destinatario no es una identidad verificada en modo sandbox, THE MSCorreos SHALL registrar un error y no enviar el correo
6. THE MSCorreos SHALL soportar múltiples destinatarios separados por punto y coma
7. THE MSCorreos SHALL validar el formato de direcciones de correo antes de enviar
8. WHEN un correo incluye adjuntos, THE MSCorreos SHALL descargar los archivos desde S3 y adjuntarlos al mensaje MIME

### Requisito 4: Tracking de Eventos de Correo

**User Story:** Como administrador, quiero rastrear el estado de los correos enviados, para conocer si fueron entregados, abiertos o rebotados.

#### Acceptance Criteria

1. THE Amazon_SES SHALL publicar Tracking_Event en un topic Amazon_SNS
2. THE MSCorreos SHALL suscribirse al topic Amazon_SNS para recibir Tracking_Event
3. WHEN se recibe un Tracking_Event de tipo "Send", THE MSCorreos SHALL registrar en Tabla_Global_Notificaciones con n_tipo="Send"
4. WHEN se recibe un Tracking_Event de tipo "Delivery", THE MSCorreos SHALL registrar en Tabla_Global_Notificaciones con n_tipo="Delivery"
5. WHEN se recibe un Tracking_Event de tipo "Open", THE MSCorreos SHALL registrar en Tabla_Global_Notificaciones con n_tipo="Open"
6. WHEN se recibe un Tracking_Event de tipo "Bounce", THE MSCorreos SHALL registrar en Tabla_Global_Notificaciones con n_tipo="Bounce[Tipo]" y n_observacion con el código de diagnóstico
7. WHEN se recibe un Tracking_Event de tipo "Complaint", THE MSCorreos SHALL registrar en Tabla_Global_Notificaciones con n_tipo="Complaint"
8. THE MSCorreos SHALL extraer de los tags del Tracking_Event: empresa, RUC, clave, tipo de notificación

### Requisito 5: Tipos de Notificaciones Soportadas

**User Story:** Como usuario del sistema, quiero enviar diferentes tipos de notificaciones por correo, para comunicar información relevante a proveedores, empleados y clientes.

#### Acceptance Criteria

1. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_VENTA_ELECTRONICA_EMITIDA" para facturas electrónicas
2. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_COMPRA_ELECTRONICA_EMITIDA" para liquidaciones de compra
3. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_GUIA_REMISION" para guías de remisión
4. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_LIQUIDACION_COMPRA" para liquidaciones de compra
5. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_PROVEEDOR_ORDEN_COMPRA" para órdenes de compra
6. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_ROL_PAGOS" para roles de pago de empleados
7. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_CUENTAS_POR_COBRAR" para estados de cuenta de clientes
8. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_PAGO_PROVEEDOR" para notificaciones de pago a proveedores
9. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_CONTABLE_ERRORES" para errores del sistema contable
10. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_PROVEEDOR_ANULACION_ORDEN_COMPRA" para anulaciones de órdenes de compra
11. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_ANTICIPO_PROVEEDOR" para anticipos a proveedores
12. THE Sistema SHALL soportar notificaciones de tipo "NOTIFICAR_PROVEEDOR_IMB" para notificaciones IMB a proveedores

### Requisito 6: Resiliencia y Manejo de Fallos

**User Story:** Como administrador del sistema, quiero que el sistema sea resiliente ante fallos, para garantizar que no se pierdan notificaciones.

#### Acceptance Criteria

1. WHEN un mensaje falla al procesarse, THE Consumidor SHALL reintentar el procesamiento hasta 3 veces con backoff exponencial
2. WHEN un mensaje falla después de 3 reintentos, THE Sistema SHALL mover el mensaje a la Dead_Letter_Queue
3. THE Sistema SHALL implementar un Circuit_Breaker para llamadas a Amazon_SES con umbral de 50% de fallos en 10 solicitudes
4. WHEN el Circuit_Breaker está abierto, THE Sistema SHALL rechazar nuevas solicitudes inmediatamente durante 60 segundos
5. THE Sistema SHALL registrar en CloudWatch todos los errores de procesamiento con nivel ERROR
6. WHEN Amazon_SES retorna error de throttling, THE Consumidor SHALL aplicar backoff exponencial antes de reintentar
7. THE Sistema SHALL mantener mensajes en la cola principal por un máximo de 14 días

### Requisito 7: Escalabilidad Horizontal

**User Story:** Como arquitecto del sistema, quiero que MSCorreos escale horizontalmente, para manejar picos de carga de miles de correos.

#### Acceptance Criteria

1. THE MSCorreos SHALL ejecutarse como contenedores en Amazon_ECS o funciones Lambda
2. WHEN la cantidad de mensajes en la cola supera 1000, THE Sistema SHALL escalar automáticamente hasta 10 instancias del Consumidor
3. WHEN la cantidad de mensajes en la cola es menor a 100, THE Sistema SHALL reducir a 1 instancia del Consumidor
4. THE Consumidor SHALL procesar mensajes en lotes de hasta 10 mensajes simultáneamente
5. THE Sistema SHALL distribuir la carga entre múltiples instancias del Consumidor mediante Amazon_SQS
6. THE Sistema SHALL configurar un límite de tasa de envío en Amazon_SES de 14 correos por segundo

### Requisito 8: Observabilidad y Monitoreo

**User Story:** Como administrador del sistema, quiero monitorear el estado del sistema de notificaciones, para detectar y resolver problemas rápidamente.

#### Acceptance Criteria

1. THE MSCorreos SHALL enviar logs estructurados a CloudWatch Logs con nivel INFO, WARN y ERROR
2. THE MSCorreos SHALL publicar métricas personalizadas en CloudWatch: correos_enviados, correos_fallidos, tiempo_procesamiento, mensajes_en_cola
3. THE Sistema SHALL crear alarmas de CloudWatch cuando la Dead_Letter_Queue contenga más de 10 mensajes
4. THE Sistema SHALL crear alarmas de CloudWatch cuando la tasa de errores supere el 5% en 5 minutos
5. THE Sistema SHALL crear alarmas de CloudWatch cuando el tiempo de procesamiento promedio supere 5 segundos
6. THE MSCorreos SHALL incluir en cada log: timestamp, nivel, mensaje, trace_id, empresa, tipo_notificacion
7. THE Sistema SHALL retener logs en CloudWatch por 30 días

### Requisito 9: Seguridad y Gestión de Credenciales

**User Story:** Como administrador de seguridad, quiero que las credenciales y datos sensibles estén protegidos, para cumplir con estándares de seguridad.

#### Acceptance Criteria

1. THE MSCorreos SHALL obtener credenciales de AWS desde AWS Secrets Manager o variables de entorno
2. THE MSCorreos SHALL usar roles de IAM para acceder a Amazon_SQS, Amazon_SES, Amazon_SNS y S3
3. THE Sistema SHALL cifrar mensajes en tránsito usando TLS 1.2 o superior
4. THE Sistema SHALL cifrar mensajes en reposo en Amazon_SQS usando KMS
5. THE MSCorreos SHALL no registrar en logs información sensible como contraseñas o claves de acceso completas
6. THE Sistema SHALL validar y sanitizar todos los inputs antes de procesarlos
7. WHEN se detecta un intento de inyección en el contenido del correo, THE MSCorreos SHALL rechazar el mensaje y registrar un evento de seguridad

### Requisito 10: Migración de Datos Históricos

**User Story:** Como administrador del sistema, quiero migrar los datos históricos de las tablas antiguas a la nueva tabla global, para no perder información histórica.

#### Acceptance Criteria

1. THE Sistema SHALL proporcionar un script de migración SQL para consolidar datos de tablas antiguas
2. THE Script_Migracion SHALL mapear campos de tablas antiguas a campos de Tabla_Global_Notificaciones
3. THE Script_Migracion SHALL preservar el n_secuencial original o generar uno nuevo si hay conflictos
4. THE Script_Migracion SHALL marcar registros migrados con un flag en n_observacion indicando la tabla de origen
5. WHEN se ejecuta la migración, THE Sistema SHALL validar la integridad de los datos migrados
6. THE Sistema SHALL generar un reporte de migración con: total de registros migrados, registros con errores, tiempo de ejecución
7. THE Sistema SHALL mantener las tablas antiguas en modo solo lectura durante 90 días después de la migración

### Requisito 11: API REST para Consulta de Notificaciones

**User Story:** Como desarrollador, quiero consultar el estado de las notificaciones mediante una API REST, para integrar con otros sistemas.

#### Acceptance Criteria

1. THE MSCorreos SHALL exponer un endpoint GET /api/v1/notificaciones para listar notificaciones
2. THE Endpoint SHALL soportar filtros por: empresa, RUC, tipo_notificacion, fecha_inicio, fecha_fin, destinatario
3. THE Endpoint SHALL soportar paginación con parámetros: page, size (máximo 100 registros por página)
4. THE Endpoint SHALL retornar respuestas en formato JSON con: n_secuencial, n_destinatario, n_fecha, n_tipo, n_tipo_notificacion, n_empresa
5. THE MSCorreos SHALL exponer un endpoint GET /api/v1/notificaciones/{id} para obtener detalles de una notificación específica
6. THE Endpoint SHALL incluir en la respuesta el n_informe completo con el JSON del evento de tracking
7. THE MSCorreos SHALL requerir autenticación mediante API Key en el header X-API-Key
8. WHEN una solicitud no incluye API Key válida, THE MSCorreos SHALL retornar HTTP 401 Unauthorized

### Requisito 12: Plantillas de Correo Personalizables

**User Story:** Como administrador, quiero personalizar las plantillas de correo por empresa, para mantener la identidad corporativa.

#### Acceptance Criteria

1. THE Sistema SHALL almacenar plantillas de correo en una tabla correos.cor_plantillas con campos: id, empresa, tipo_notificacion, asunto_template, cuerpo_html_template, cuerpo_texto_template
2. THE Plantilla SHALL soportar variables de sustitución usando sintaxis {{variable}}
3. WHEN se envía una notificación, THE MSCorreos SHALL cargar la plantilla correspondiente a la empresa y tipo_notificacion
4. THE MSCorreos SHALL sustituir variables en la plantilla con valores del Evento_Notificacion
5. WHERE no existe plantilla personalizada, THE MSCorreos SHALL usar una plantilla por defecto
6. THE Sistema SHALL validar la sintaxis de las plantillas antes de guardarlas
7. THE Sistema SHALL soportar variables comunes: {{nombre_receptor}}, {{nombre_emisor}}, {{numero_comprobante}}, {{clave_acceso}}, {{valor}}, {{fecha}}

### Requisito 13: Gestión de Adjuntos en S3

**User Story:** Como usuario del sistema, quiero que los adjuntos de correo se gestionen eficientemente, para reducir el tamaño de los mensajes en la cola.

#### Acceptance Criteria

1. WHEN un Productor necesita enviar adjuntos, THE Productor SHALL subir los archivos a un bucket S3 y incluir las referencias en el Evento_Notificacion
2. THE Evento_Notificacion SHALL contener un array de adjuntos con: s3_bucket, s3_key, nombre_archivo, content_type
3. WHEN el Consumidor procesa el mensaje, THE MSCorreos SHALL descargar los adjuntos desde S3
4. THE MSCorreos SHALL eliminar los adjuntos de S3 después de enviar el correo exitosamente
5. THE Sistema SHALL configurar una política de ciclo de vida en S3 para eliminar archivos no procesados después de 7 días
6. THE Sistema SHALL validar que el tamaño total de adjuntos no supere 10 MB
7. WHEN un adjunto no puede descargarse de S3, THE MSCorreos SHALL registrar un error y enviar el correo sin el adjunto

### Requisito 14: Reintentos Inteligentes

**User Story:** Como administrador del sistema, quiero que los reintentos sean inteligentes, para no saturar el sistema con mensajes problemáticos.

#### Acceptance Criteria

1. WHEN un mensaje falla por error temporal (throttling, timeout), THE Consumidor SHALL reintentar con backoff exponencial: 1s, 2s, 4s
2. WHEN un mensaje falla por error permanente (destinatario inválido, contenido rechazado), THE Consumidor SHALL mover el mensaje inmediatamente a la Dead_Letter_Queue sin reintentos
3. THE Consumidor SHALL incluir en el mensaje el contador de reintentos en el atributo retry_count
4. THE Sistema SHALL registrar en CloudWatch cada reintento con el motivo del fallo
5. WHEN un mensaje en la Dead_Letter_Queue es reprocesado manualmente, THE Sistema SHALL reiniciar el contador de reintentos
6. THE Sistema SHALL implementar jitter aleatorio en el backoff exponencial para evitar thundering herd

### Requisito 15: Soporte para Correos Transaccionales y Masivos

**User Story:** Como usuario del sistema, quiero diferenciar entre correos transaccionales y masivos, para aplicar diferentes políticas de envío.

#### Acceptance Criteria

1. THE Evento_Notificacion SHALL incluir un campo prioridad con valores: ALTA, MEDIA, BAJA
2. WHEN un Evento_Notificacion tiene prioridad ALTA, THE Consumidor SHALL procesarlo antes que mensajes de prioridad MEDIA o BAJA
3. THE Sistema SHALL usar colas SQS FIFO para mensajes de prioridad ALTA
4. THE Sistema SHALL usar colas SQS estándar para mensajes de prioridad MEDIA y BAJA
5. WHEN se envían correos masivos (más de 100 destinatarios), THE Productor SHALL dividir el envío en múltiples Evento_Notificacion
6. THE Sistema SHALL limitar el envío de correos masivos a 1000 correos por hora por empresa
7. THE Sistema SHALL registrar en CloudWatch métricas separadas para correos transaccionales y masivos

### Requisito 16: Gestión de Lista Negra de Correos

**User Story:** Como administrador del sistema, quiero gestionar una lista negra de correos electrónicos, para evitar enviar correos a direcciones problemáticas y prevenir multas de AWS SES.

#### Acceptance Criteria

1. THE Sistema SHALL mantener una tabla correos.cor_lista_negra con campos: id, email, motivo, fecha_registro, tipo_bloqueo, activo
2. WHEN el Consumidor procesa un Evento_Notificacion, THE MSCorreos SHALL consultar la tabla cor_lista_negra ANTES de enviar a Amazon_SES
3. WHEN un destinatario está en la lista negra con activo=true, THE MSCorreos SHALL rechazar el envío inmediatamente sin llamar a Amazon_SES
4. WHEN un destinatario está en la lista negra, THE MSCorreos SHALL registrar en Tabla_Global_Notificaciones con n_tipo="Blocked" y n_observacion con el motivo del bloqueo
5. THE Sistema SHALL agregar automáticamente a la lista negra correos que generen eventos de tipo "Bounce" con bounceType="Permanent"
6. THE Sistema SHALL agregar automáticamente a la lista negra correos que generen eventos de tipo "Complaint"
7. THE Sistema SHALL clasificar bloqueos con tipo_bloqueo: HARD_BOUNCE, SOFT_BOUNCE_REPETIDO, COMPLAINT, MANUAL
8. WHEN un correo genera 3 o más "Soft Bounce" en 30 días, THE Sistema SHALL agregarlo a la lista negra con tipo_bloqueo="SOFT_BOUNCE_REPETIDO"
9. THE Sistema SHALL permitir desbloquear manualmente correos de la lista negra mediante API REST
10. THE MSCorreos SHALL exponer un endpoint POST /api/v1/lista-negra para agregar correos manualmente
11. THE MSCorreos SHALL exponer un endpoint DELETE /api/v1/lista-negra/{email} para remover correos de la lista negra
12. THE MSCorreos SHALL exponer un endpoint GET /api/v1/lista-negra para consultar correos bloqueados con filtros por: motivo, tipo_bloqueo, fecha_desde, fecha_hasta
13. WHEN se intenta enviar a múltiples destinatarios y alguno está en lista negra, THE MSCorreos SHALL filtrar solo los destinatarios bloqueados y enviar a los válidos
14. THE Sistema SHALL registrar en CloudWatch métricas de: correos_bloqueados_por_lista_negra, total_correos_en_lista_negra
15. THE Sistema SHALL crear una alarma de CloudWatch cuando la lista negra supere 1000 correos
16. THE Sistema SHALL mantener un historial de cambios en la lista negra en tabla correos.cor_lista_negra_historial con: id, email, accion (AGREGAR/REMOVER), motivo, usuario, fecha

## Requisitos No Funcionales

### Requisito NF1: Performance

**User Story:** Como usuario del sistema, quiero que los correos se envíen rápidamente, para que los destinatarios reciban la información a tiempo.

#### Acceptance Criteria

1. THE Sistema SHALL procesar un Evento_Notificacion en menos de 2 segundos en el percentil 95
2. THE Sistema SHALL enviar un correo mediante Amazon_SES en menos de 1 segundo en el percentil 95
3. THE Sistema SHALL soportar el envío de al menos 10,000 correos por hora
4. THE API REST SHALL responder consultas en menos de 500 ms en el percentil 95
5. THE Sistema SHALL mantener una latencia de cola menor a 30 segundos en condiciones normales

### Requisito NF2: Disponibilidad

**User Story:** Como administrador del sistema, quiero que el sistema esté disponible 24/7, para no interrumpir las operaciones del negocio.

#### Acceptance Criteria

1. THE MSCorreos SHALL tener una disponibilidad del 99.5% mensual
2. THE Sistema SHALL implementar health checks en el endpoint GET /health que retorne HTTP 200 cuando el servicio está operativo
3. WHEN el health check falla 3 veces consecutivas, THE Sistema SHALL reiniciar automáticamente el contenedor
4. THE Sistema SHALL distribuir instancias del Consumidor en múltiples zonas de disponibilidad de AWS
5. THE Sistema SHALL mantener al menos 2 instancias del Consumidor activas en todo momento

### Requisito NF3: Mantenibilidad

**User Story:** Como desarrollador, quiero que el código sea mantenible, para facilitar futuras modificaciones y correcciones.

#### Acceptance Criteria

1. THE MSCorreos SHALL implementar Clean Architecture con capas: Domain, Application, Infrastructure, Presentation
2. THE Código SHALL tener una cobertura de pruebas unitarias del 80% mínimo
3. THE Código SHALL seguir los principios SOLID
4. THE Sistema SHALL usar inyección de dependencias mediante Spring Framework
5. THE Código SHALL estar documentado con Javadoc en clases y métodos públicos
6. THE Sistema SHALL usar nombres de variables y métodos descriptivos en español o inglés de forma consistente

### Requisito NF4: Auditoría

**User Story:** Como auditor, quiero tener trazabilidad completa de todas las notificaciones, para cumplir con requisitos regulatorios.

#### Acceptance Criteria

1. THE Sistema SHALL registrar en Tabla_Global_Notificaciones cada evento de tracking con timestamp preciso
2. THE Sistema SHALL preservar el JSON completo del evento de Amazon_SNS en el campo n_informe
3. THE Sistema SHALL mantener registros de notificaciones por al menos 7 años
4. THE Sistema SHALL permitir exportar registros de notificaciones en formato CSV
5. THE Sistema SHALL registrar en logs cada acceso a la API REST con: timestamp, usuario, endpoint, parámetros

### Requisito NF5: Compatibilidad

**User Story:** Como desarrollador, quiero que el sistema sea compatible con la infraestructura existente, para facilitar la integración.

#### Acceptance Criteria

1. THE MSCorreos SHALL ejecutarse en Java 11 o superior
2. THE MSCorreos SHALL usar Spring Boot 2.7 o superior
3. THE MSCorreos SHALL conectarse a PostgreSQL 12 o superior
4. THE MSCorreos SHALL ser compatible con AWS SDK for Java 2.x
5. THE Sistema SHALL exponer métricas en formato Prometheus para integración con sistemas de monitoreo existentes

## Estrategia de Migración

### Fase 1: Preparación (Semana 1-2)

1. Crear la tabla correos.cor_notificaciones en el esquema de base de datos
2. Crear la tabla correos.cor_plantillas para plantillas personalizables
3. Crear la tabla correos.cor_lista_negra para gestión de correos bloqueados
4. Crear la tabla correos.cor_lista_negra_historial para auditoría de cambios en lista negra
5. Configurar infraestructura AWS: colas SQS, topics SNS, Configuration Set en SES, buckets S3
6. Configurar roles de IAM y políticas de seguridad
7. Implementar scripts de migración de datos históricos
8. Migrar correos problemáticos existentes a la lista negra inicial

### Fase 2: Desarrollo del Nuevo Sistema (Semana 3-6)

1. Implementar MSCorreos con Clean Architecture
2. Desarrollar adaptadores para Amazon_SQS, Amazon_SES, Amazon_SNS
3. Implementar lógica de procesamiento de eventos y envío de correos
4. Desarrollar API REST para consulta de notificaciones
5. Implementar sistema de plantillas personalizables
6. Desarrollar pruebas unitarias e integración

### Fase 3: Migración Gradual (Semana 7-10)

1. Ejecutar scripts de migración de datos históricos en ambiente de pruebas
2. Desplegar MSCorreos en ambiente de pruebas
3. Configurar productores en ShrimpSoftServer para publicar eventos en SQS (modo dual: tabla antigua + SQS)
4. Validar funcionamiento en ambiente de pruebas con casos reales
5. Migrar un módulo piloto (ej: notificaciones de error) a producción
6. Monitorear y ajustar configuración basado en métricas

### Fase 4: Migración Completa (Semana 11-12)

1. Migrar todos los módulos restantes a la nueva arquitectura
2. Ejecutar scripts de migración de datos históricos en producción
3. Desactivar escritura en tablas antiguas de notificaciones
4. Configurar tablas antiguas en modo solo lectura
5. Monitorear sistema durante 2 semanas para detectar problemas

### Fase 5: Limpieza (Semana 13-14)

1. Eliminar código legacy de UtilsMail.java y EnviarCorreoServiceImpl.java
2. Archivar tablas antiguas de notificaciones
3. Documentar la nueva arquitectura
4. Capacitar al equipo en el nuevo sistema
5. Establecer procedimientos de operación y mantenimiento

## Restricciones Técnicas

1. El sistema debe ejecutarse en la región us-east-1 de AWS por compatibilidad con infraestructura existente
2. El sistema debe mantener compatibilidad con el esquema de tags actual de Amazon_SES (ows-*)
3. El sistema debe soportar el formato actual de eventos de Amazon_SNS sin cambios
4. El sistema debe mantener el formato de Clave_Acceso de 49 dígitos para comprobantes electrónicos
5. El sistema debe respetar los límites de Amazon_SES: 14 correos/segundo, 10 MB por mensaje
6. El sistema debe usar PostgreSQL como base de datos por estándar corporativo
7. El sistema debe implementarse en Java con Spring Boot por estándar corporativo

## Criterios de Aceptación del Proyecto

1. Todas las notificaciones se almacenan en correos.cor_notificaciones
2. MSCorreos consulta la lista negra ANTES de enviar cualquier correo a Amazon_SES
3. MSCorreos procesa eventos desde Amazon_SQS y envía correos mediante Amazon_SES
4. El sistema registra todos los eventos de tracking (Send, Delivery, Open, Bounce, Complaint)
5. El sistema agrega automáticamente a la lista negra correos con Hard Bounce, Complaint y Soft Bounce repetidos
6. El sistema maneja fallos con reintentos y Dead_Letter_Queue
7. El sistema escala automáticamente según la carga
8. El sistema tiene observabilidad completa con logs y métricas en CloudWatch
9. La API REST permite consultar el estado de notificaciones y gestionar la lista negra
10. Los datos históricos se migraron exitosamente sin pérdida de información
11. El sistema cumple con los requisitos no funcionales de performance, disponibilidad y seguridad
12. La documentación técnica está completa y el equipo está capacitado
13. No se envían correos a direcciones en lista negra, evitando multas de AWS SES
