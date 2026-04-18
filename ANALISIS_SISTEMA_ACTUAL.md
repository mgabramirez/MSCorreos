# Análisis del Sistema Actual de Envío de Correos

## Resumen Ejecutivo

El sistema actual de ShrimpSoftServer envía correos electrónicos de forma **dispersa** usando múltiples tablas de notificaciones por módulo. Este documento analiza el código existente para entender cómo migrar a una arquitectura centralizada con MSCorreos.

---

## 1. Sistema Actual de Envío

### 1.1 Clase Principal: `UtilsMail.java`

**Ubicación**: `ShrimpSoftServer/src/main/java/ec/com/todocompu/ShrimpSoftServer/util/UtilsMail.java`

**Método principal de envío**:
```java
public static String envioCorreoPersonalizadoAmazonSES(
    SisEmailComprobanteElectronicoTO sisEmailComprobanteElectronicoTO,
    String destinatarios,
    String asunto,
    String detalle,
    String detalleTextoPlano,
    List<File> listAdjunto,
    SisNotificacion parametro
)
```

**Flujo actual**:
1. Valida destinatarios (separados por `;`)
2. Construye mensaje MIME con HTML + texto plano + adjuntos
3. Establece tags de SES según tipo de notificación
4. Envía mediante `AmazonSimpleEmailService.sendRawEmail()`
5. Usa Configuration Set para tracking

**Tags SES actuales**:
- `ows-tipo-notificacion`: Tipo de notificación
- `ows-empresa`: Código de empresa
- `ows-ruc`: RUC del emisor
- `ows-clave`: Clave del documento
- `ows-clave-acceso`: Clave de acceso (49 dígitos para comprobantes electrónicos)
- Otros específicos por tipo (sector, motivo, número, etc.)

### 1.2 Servicio: `EnviarCorreoServiceImpl.java`

**Ubicación**: `ShrimpSoftServer/src/main/java/ec/com/todocompu/ShrimpSoftServer/util/service/EnviarCorreoServiceImpl.java`

**Responsabilidades**:
- Construir HTML del correo con header/footer personalizado
- Generar PDFs adjuntos
- Llamar a `UtilsMail` para envío
- **Registrar en tablas de notificaciones dispersas**

---

## 2. Tablas de Notificaciones Dispersas (Sistema Actual)

### 2.1 Módulo: Comprobantes Electrónicos

#### `anexo.anx_venta_electronica_notificaciones`
```sql
CREATE TABLE anexo.anx_venta_electronica_notificaciones (
    e_secuencial serial PRIMARY KEY,
    e_destinatario text NOT NULL,
    e_fecha timestamp NOT NULL,
    e_tipo text NOT NULL,
    e_observacion text,
    e_informe text NOT NULL,
    ven_empresa text NOT NULL,
    ven_periodo text NOT NULL,
    ven_motivo text NOT NULL,
    ven_numero text NOT NULL
);
```

**Tipos de notificación**:
- `NOTIFICAR_VENTA_ELECTRONICA_EMITIDA`

#### `anexo.anx_compra_electronica_notificaciones`
```sql
CREATE TABLE anexo.anx_compra_electronica_notificaciones (
    e_secuencial serial PRIMARY KEY,
    e_destinatario text NOT NULL,
    e_fecha timestamp NOT NULL,
    e_tipo text NOT NULL,
    e_observacion text,
    e_informe text NOT NULL,
    com_empresa text NOT NULL,
    com_periodo text NOT NULL,
    com_motivo text NOT NULL,
    com_numero text NOT NULL
);
```

**Tipos de notificación**:
- `NOTIFICAR_COMPRA_ELECTRONICA_EMITIDA`
- `NOTIFICAR_LIQUIDACION_COMPRA`

#### `anexo.anx_guia_remision_electronica_notificaciones`
```sql
CREATE TABLE anexo.anx_guia_remision_electronica_notificaciones (
    e_secuencial serial PRIMARY KEY,
    e_destinatario text NOT NULL,
    e_fecha timestamp NOT NULL,
    e_tipo text NOT NULL,
    e_observacion text,
    e_informe text NOT NULL,
    guia_empresa text NOT NULL,
    guia_periodo text NOT NULL,
    guia_motivo text NOT NULL,
    guia_numero text NOT NULL
);
```

**Tipos de notificación**:
- `NOTIFICAR_GUIA_REMISION`

### 2.2 Módulo: Inventario/Pedidos

#### `inventario.inv_pedidos_orden_compra_notificaciones`
```sql
CREATE TABLE inventario.inv_pedidos_orden_compra_notificaciones (
    ocn_secuencial serial PRIMARY KEY,
    ocn_destinatario text NOT NULL,
    ocn_fecha timestamp NOT NULL,
    ocn_tipo text NOT NULL,
    ocn_observacion text,
    ocn_informe text NOT NULL,
    oc_empresa text NOT NULL,
    oc_sector text NOT NULL,
    oc_motivo text NOT NULL,
    oc_numero text NOT NULL
);
```

**Tipos de notificación**:
- `NOTIFICAR_PROVEEDOR_ORDEN_COMPRA`

#### `inventario.inv_pedidos_orden_compra_anulada_notificaciones`
```sql
CREATE TABLE inventario.inv_pedidos_orden_compra_anulada_notificaciones (
    ocan_secuencial serial PRIMARY KEY,
    ocan_destinatario text NOT NULL,
    ocan_fecha timestamp NOT NULL,
    ocan_tipo text NOT NULL,
    ocan_observacion text,
    ocan_informe text NOT NULL,
    oc_empresa text NOT NULL,
    oc_sector text NOT NULL,
    oc_motivo text NOT NULL,
    oc_numero text NOT NULL
);
```

**Tipos de notificación**:
- `NOTIFICAR_PROVEEDOR_ANULACION_ORDEN_COMPRA`

#### `inventario.inv_cliente_notificaciones`
```sql
CREATE TABLE inventario.inv_cliente_notificaciones (
    e_secuencial serial PRIMARY KEY,
    e_destinatario text NOT NULL,
    e_fecha timestamp NOT NULL,
    e_tipo text NOT NULL,
    e_observacion text,
    e_informe text NOT NULL,
    cli_empresa text NOT NULL,
    cli_codigo text NOT NULL,
    asunto text NOT NULL,
    motivo text NOT NULL
);
```

**Tipos de notificación**:
- Notificaciones genéricas a clientes

#### `inventario.inv_imb_notificaciones`
```sql
CREATE TABLE inventario.inv_imb_notificaciones (
    in_secuencial serial PRIMARY KEY,
    in_destinatario text NOT NULL,
    in_fecha timestamp NOT NULL,
    in_tipo text NOT NULL,
    in_observacion text,
    in_informe text NOT NULL,
    in_empresa text NOT NULL,
    in_proveedor text
);
```

**Tipos de notificación**:
- `NOTIFICAR_PROVEEDOR_IMB`

### 2.3 Módulo: Recursos Humanos

#### `recursoshumanos.rh_rol_pago_notificaciones`
```sql
CREATE TABLE recursoshumanos.rh_rol_pago_notificaciones (
    rpn_secuencial serial PRIMARY KEY,
    rpn_destinatario text NOT NULL,
    rpn_fecha timestamp NOT NULL,
    rpn_tipo text NOT NULL,
    rpn_observacion text,
    rpn_informe text NOT NULL,
    rpn_contable text NOT NULL,
    rpn_empresa text NOT NULL
);
```

**Tipos de notificación**:
- `NOTIFICAR_ROL_PAGOS`

### 2.4 Módulo: Cartera

#### `cartera.car_pagos_anticipos_notificaciones`
```sql
CREATE TABLE cartera.car_pagos_anticipos_notificaciones (
    cpa_secuencial serial PRIMARY KEY,
    cpa_destinatario text NOT NULL,
    cpa_fecha timestamp NOT NULL,
    cpa_tipo text NOT NULL,
    cpa_observacion text,
    cpa_informe text NOT NULL,
    cpa_empresa text NOT NULL
);
```

**Tipos de notificación**:
- `NOTIFICAR_ANTICIPO_PROVEEDOR`

#### Tabla implícita: Cuentas por Cobrar
No tiene tabla específica, pero usa:
- `NOTIFICAR_CUENTAS_POR_COBRAR`
- `NOTIFICAR_PAGO_PROVEEDOR`

### 2.5 Módulo: Contabilidad

#### `contabilidad.con_verificacion_errores_notificaciones`
```sql
CREATE TABLE contabilidad.con_verificacion_errores_notificaciones (
    ven_secuencial serial PRIMARY KEY,
    ven_destinatario text NOT NULL,
    ven_fecha timestamp NOT NULL,
    ven_tipo text NOT NULL,
    ven_observacion text,
    ven_informe text NOT NULL,
    ven_empresa text NOT NULL
);
```

**Tipos de notificación**:
- `NOTIFICAR_CONTABLE_ERRORES`

#### `sistemaweb.sis_notificaciones_errores_contabilidad`
```sql
CREATE TABLE sistemaweb.sis_notificaciones_errores_contabilidad (
    notificacion_empresa text,
    notificacion_fecha timestamp,
    notificacion_tipo text,
    notificacion_observacion text
);
```

#### `sistemaweb.sis_notificaciones_errores_inventario`
```sql
CREATE TABLE sistemaweb.sis_notificaciones_errores_inventario (
    notificacion_empresa text,
    notificacion_fecha timestamp,
    notificacion_tipo text,
    notificacion_observacion text
);
```

---

## 3. Tipos de Notificaciones Identificados

### 3.1 Enum: `TipoNotificacion`

**Ubicación**: `ShrimpSoftUtils/src/main/java/ec/com/todocompu/ShrimpSoftUtils/enums/TipoNotificacion.java`

```java
public enum TipoNotificacion {
    NOTIFICAR_VENTA_ELECTRONICA_EMITIDA,
    NOTIFICAR_COMPRA_ELECTRONICA_EMITIDA,
    NOTIFICAR_GUIA_REMISION,
    NOTIFICAR_LIQUIDACION_COMPRA,
    NOTIFICAR_PROVEEDOR_ORDEN_COMPRA,
    NOTIFICAR_ROL_PAGOS,
    NOTIFICAR_CUENTAS_POR_COBRAR,
    NOTIFICAR_PAGO_PROVEEDOR,
    NOTIFICAR_CONTABLE_ERRORES,
    NOTIFICAR_PROVEEDOR_ANULACION_ORDEN_COMPRA,
    NOTIFICAR_ANTICIPO_PROVEEDOR,
    NOTIFICAR_PROVEEDOR_IMB
}
```

### 3.2 Mapeo a Tabla Unificada

Todos estos tipos se consolidarán en `correos.cor_notificaciones` con el campo `n_tipo_notificacion`.

---

## 4. Objeto de Transferencia: `SisEmailComprobanteElectronicoTO`

**Ubicación**: `ShrimpSoftUtils/src/main/java/ec/com/todocompu/ShrimpSoftUtils/sistema/TO/SisEmailComprobanteElectronicoTO.java`

**Campos principales**:
```java
public class SisEmailComprobanteElectronicoTO {
    private String empresa;
    private String rucEmisor;
    private String nombreEmisor;
    private String mailEmisor;
    private String claveEmisor; // Configuration Set de SES
    private String tipoComprobante; // Tipo de notificación
    private String claveAcceso; // 49 dígitos para comprobantes electrónicos
    private String periodo;
    private String motivo;
    private String numero;
    // ... otros campos
}
```

**Este objeto será la base para el DTO `EventoNotificacion` en MSCorreos**.

---

## 5. Problema Actual: Dispersión de Datos

### 5.1 Problemas Identificados

1. **12+ tablas diferentes** para notificaciones
2. **Esquemas diferentes** por cada tabla (campos específicos por módulo)
3. **Duplicación de lógica** de envío en múltiples servicios
4. **Difícil consultar** notificaciones globales
5. **No hay lista negra centralizada** (validación dispersa)
6. **Acoplamiento alto** entre módulos y lógica de correo

### 5.2 Campos Comunes en Todas las Tablas

Todas las tablas actuales tienen estos campos:
- `*_secuencial`: ID autoincremental
- `*_destinatario`: Email del destinatario
- `*_fecha`: Timestamp del evento
- `*_tipo`: Tipo de evento (Send, Delivery, Bounce, etc.)
- `*_observacion`: Observaciones adicionales
- `*_informe`: JSON del evento de tracking
- `*_empresa`: Código de empresa

**Campos específicos por módulo**:
- Comprobantes: `periodo`, `motivo`, `numero`, `clave_acceso`
- Órdenes de compra: `sector`, `motivo`, `numero`
- Roles de pago: `contable`
- Clientes: `cli_codigo`, `asunto`, `motivo`

---

## 6. Solución: Tabla Unificada `cor_notificaciones`

### 6.1 Diseño de la Tabla Unificada

```sql
CREATE TABLE correos.cor_notificaciones (
    n_secuencial SERIAL PRIMARY KEY,
    n_destinatario TEXT NOT NULL,
    n_fecha TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    n_tipo TEXT NOT NULL,
    n_observacion TEXT,
    n_informe TEXT NOT NULL, -- JSON completo
    n_empresa TEXT NOT NULL,
    n_ruc TEXT,
    n_clave TEXT, -- Clave genérica (periodo_motivo_numero o contable)
    n_tipo_notificacion TEXT NOT NULL -- Enum TipoNotificacion
);
```

### 6.2 Mapeo de Campos Específicos

Los campos específicos de cada módulo se almacenarán en:
1. **`n_clave`**: Concatenación de identificadores (ej: `2024_001_00001`)
2. **`n_informe`**: JSON completo con todos los detalles

**Ejemplo de `n_informe` para orden de compra**:
```json
{
  "sector": "001",
  "motivo": "COMPRA",
  "numero": "00001",
  "proveedor": "PROV001",
  "valor": 1500.00,
  "tracking": {
    "messageId": "abc123",
    "timestamp": "2024-01-09T10:30:00Z"
  }
}
```

---

## 7. Estrategia de Migración

### 7.1 Fase 1: Doble Escritura (Transición)

Durante la migración, ShrimpSoftServer escribirá en:
1. **Tabla antigua** (mantener compatibilidad)
2. **Cola SQS** (nuevo sistema)

```java
// Pseudocódigo
public void enviarNotificacion(...) {
    // 1. Enviar correo (actual)
    String resultado = UtilsMail.envioCorreoPersonalizadoAmazonSES(...);
    
    // 2. Registrar en tabla antigua (actual)
    registrarEnTablaAntigua(...);
    
    // 3. Publicar evento en SQS (nuevo)
    publicarEventoSQS(...);
}
```

### 7.2 Fase 2: Solo SQS

Una vez validado MSCorreos:
1. Eliminar escritura en tablas antiguas
2. Solo publicar en SQS
3. MSCorreos registra en `cor_notificaciones`

### 7.3 Fase 3: Migración de Datos Históricos

Script SQL para consolidar datos:
```sql
-- Migrar ventas electrónicas
INSERT INTO correos.cor_notificaciones 
    (n_destinatario, n_fecha, n_tipo, n_observacion, n_informe, 
     n_empresa, n_ruc, n_clave, n_tipo_notificacion)
SELECT 
    e_destinatario,
    e_fecha,
    e_tipo,
    e_observacion || ' [MIGRADO_DE: anx_venta_electronica_notificaciones]',
    e_informe,
    ven_empresa,
    NULL, -- RUC se extrae del JSON si existe
    ven_periodo || '_' || ven_motivo || '_' || ven_numero,
    'NOTIFICAR_VENTA_ELECTRONICA_EMITIDA'
FROM anexo.anx_venta_electronica_notificaciones;

-- Repetir para cada tabla...
```

---

## 8. Arquitectura Nueva: MSCorreos

### 8.1 Flujo Completo

```
ShrimpSoftServer (Productor)
    ↓
    1. Construir EventoNotificacion
    2. Subir adjuntos a S3 (si aplica)
    3. Publicar en SQS (Standard o FIFO)
    ↓
Amazon SQS
    ↓
MSCorreos (Consumidor)
    ↓
    1. Polling de mensajes
    2. Validar lista negra
    3. Cargar plantilla
    4. Construir mensaje MIME
    5. Descargar adjuntos de S3
    6. Enviar mediante SES
    7. Registrar en cor_notificaciones
    8. Eliminar adjuntos de S3
    ↓
Amazon SES
    ↓
Amazon SNS (Tracking)
    ↓
MSCorreos (SNS Listener)
    ↓
    1. Recibir evento de tracking
    2. Registrar en cor_notificaciones
    3. Actualizar lista negra (si bounce/complaint)
```

### 8.2 Ventajas de la Nueva Arquitectura

1. **Centralización**: Una sola tabla para todas las notificaciones
2. **Desacoplamiento**: ShrimpSoftServer no envía correos directamente
3. **Escalabilidad**: MSCorreos escala independientemente
4. **Resiliencia**: Reintentos automáticos, DLQ, circuit breaker
5. **Observabilidad**: Logs, métricas y alarmas centralizadas
6. **Lista negra**: Validación automática antes de enviar
7. **Tracking completo**: Todos los eventos en una sola tabla

---

## 9. Próximos Pasos

### 9.1 Implementación Inmediata

1. ✅ **Task 1**: Crear tabla `cor_notificaciones` (COMPLETADO)
2. ✅ **Task 2.1-2.5**: Configurar infraestructura AWS (COMPLETADO)
3. ⏭️ **Task 2.6**: Configurar roles IAM
4. ⏭️ **Task 4-9**: Implementar MSCorreos (Domain, Application, Infrastructure, Presentation)
5. ⏭️ **Task 16**: Implementar productores en ShrimpSoftServer

### 9.2 Validación

1. Probar envío de cada tipo de notificación
2. Verificar registro en `cor_notificaciones`
3. Validar tracking de eventos
4. Confirmar lista negra funciona
5. Migrar datos históricos

---

## 10. Conclusiones

El sistema actual está **altamente disperso** con 12+ tablas de notificaciones. La migración a `correos.cor_notificaciones` centralizará todo el sistema, mejorará la mantenibilidad y permitirá escalabilidad horizontal mediante MSCorreos como microservicio independiente.

**Clave del éxito**: Implementar doble escritura durante la transición para no perder datos ni interrumpir operaciones.
