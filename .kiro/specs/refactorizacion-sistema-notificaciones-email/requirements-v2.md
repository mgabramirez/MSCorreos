# Requisitos: Centralización de Notificaciones en MSCorreos

## 1. Contexto

### Sistema Actual (ShrimpSoftServer)
- Envía correos usando `UtilsMail.envioCorreoPersonalizadoAmazonSES()`
- Registra en **12+ tablas dispersas** por módulo
- Usa Amazon SES directamente con tags `ows-*`
- Cada módulo tiene su propia tabla de notificaciones

### Problema
- Imposible consultar todas las notificaciones en un solo lugar
- Duplicación de esquemas y lógica
- Difícil mantenimiento y auditoría
- No hay lista negra centralizada

### Solución
- Tabla única: `correos.cor_notificaciones`
- MSCorreos como único responsable de registrar notificaciones
- Mantener compatibilidad con sistema actual de envío

---

## 2. Requisito: Tabla Unificada de Notificaciones

**Como** administrador del sistema  
**Quiero** que todas las notificaciones se registren en una única tabla  
**Para** poder consultarlas y auditarlas fácilmente

### Criterios de Aceptación

1. MSCorreos SHALL usar la tabla `correos.cor_notificaciones` con estructura:
```sql
CREATE TABLE correos.cor_notificaciones (
    n_secuencial SERIAL PRIMARY KEY,
    n_destinatario TEXT NOT NULL,
    n_fecha TIMESTAMP NOT NULL,
    n_tipo TEXT NOT NULL,
    n_observacion TEXT,
    n_informe TEXT NOT NULL,
    n_empresa TEXT NOT NULL,
    n_ruc TEXT,
    n_clave TEXT,
    n_tipo_notificacion TEXT NOT NULL
);
```

2. El campo `n_tipo_notificacion` SHALL contener uno de:
   - NOTIFICAR_VENTA_ELECTRONICA_EMITIDA
   - NOTIFICAR_COMPRA_ELECTRONICA_EMITIDA
   - NOTIFICAR_GUIA_REMISION
   - NOTIFICAR_LIQUIDACION_COMPRA
   - NOTIFICAR_PROVEEDOR_ORDEN_COMPRA
   - NOTIFICAR_ROL_PAGOS
   - NOTIFICAR_CUENTAS_POR_COBRAR
   - NOTIFICAR_PAGO_PROVEEDOR
   - NOTIFICAR_CONTABLE_ERRORES
   - NOTIFICAR_PROVEEDOR_ANULACION_ORDEN_COMPRA
   - NOTIFICAR_ANTICIPO_PROVEEDOR
   - NOTIFICAR_PROVEEDOR_IMB

3. El campo `n_informe` SHALL contener el JSON completo del evento

4. El campo `n_clave` SHALL contener identificador único del documento (ej: `2024_001_00001`)

---

## 3. Requisito: Compatibilidad con Sistema Actual

**Como** desarrollador  
**Quiero** que MSCorreos use la misma lógica de envío actual  
**Para** no romper funcionalidad existente

### Criterios de Aceptación

1. MSCorreos SHALL enviar correos usando Amazon SES igual que `UtilsMail.java`

2. MSCorreos SHALL usar los mismos tags SES:
   - `ows-tipo-notificacion`
   - `ows-empresa`
   - `ows-ruc`
   - `ows-clave`
   - `ows-clave-acceso` (para comprobantes electrónicos)

3. MSCorreos SHALL construir mensajes MIME con:
   - HTML
   - Texto plano
   - Adjuntos (si aplica)

4. MSCorreos SHALL usar Configuration Set de SES para tracking

---

## 4. Requisito: Registro de Eventos de Tracking

**Como** administrador  
**Quiero** que se registren todos los eventos de SES  
**Para** saber el estado de cada correo

### Criterios de Aceptación

1. MSCorreos SHALL registrar eventos de tipo:
   - Send
   - Delivery
   - Open
   - Bounce (Transient/Permanent)
   - Complaint

2. Cada evento SHALL crear un registro en `cor_notificaciones` con:
   - Mismo `n_empresa`, `n_ruc`, `n_clave` del envío original
   - `n_tipo` = tipo de evento
   - `n_informe` = JSON del evento SNS

---

## 5. Requisito: Lista Negra de Correos

**Como** administrador  
**Quiero** una lista negra centralizada  
**Para** evitar enviar a correos problemáticos

### Criterios de Aceptación

1. MSCorreos SHALL usar tabla `correos.cor_lista_negra`:
```sql
CREATE TABLE correos.cor_lista_negra (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    motivo TEXT NOT NULL,
    fecha_registro TIMESTAMP NOT NULL,
    tipo_bloqueo VARCHAR(50) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE
);
```

2. MSCorreos SHALL validar destinatarios contra lista negra ANTES de enviar

3. MSCorreos SHALL registrar evento "Blocked" si destinatario está en lista negra

4. MSCorreos SHALL agregar automáticamente a lista negra:
   - Bounce Permanent → tipo_bloqueo = 'HARD_BOUNCE'
   - Complaint → tipo_bloqueo = 'COMPLAINT'
   - 3+ Soft Bounce en 30 días → tipo_bloqueo = 'SOFT_BOUNCE_REPETIDO'

---

## 6. Requisito: API REST para Consultas

**Como** desarrollador  
**Quiero** consultar notificaciones mediante API  
**Para** integrar con otros sistemas

### Criterios de Aceptación

1. MSCorreos SHALL exponer endpoint:
```
GET /api/v1/notificaciones?empresa={empresa}&tipo={tipo}&fecha_desde={fecha}&fecha_hasta={fecha}
```

2. MSCorreos SHALL retornar JSON con paginación:
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 100
}
```

3. MSCorreos SHALL requerir API Key en header `X-API-Key`

---

## 7. Requisito: Migración de Datos Históricos

**Como** administrador  
**Quiero** migrar datos de tablas antiguas  
**Para** no perder historial

### Criterios de Aceptación

1. MSCorreos SHALL proporcionar script SQL que migre datos de:
   - `anexo.anx_venta_electronica_notificaciones`
   - `anexo.anx_compra_electronica_notificaciones`
   - `anexo.anx_guia_remision_electronica_notificaciones`
   - `inventario.inv_pedidos_orden_compra_notificaciones`
   - `recursoshumanos.rh_rol_pago_notificaciones`
   - `cartera.car_pagos_anticipos_notificaciones`
   - Y demás tablas dispersas

2. El script SHALL mapear campos específicos a campos genéricos:
   - `ven_periodo + ven_motivo + ven_numero` → `n_clave`
   - Tipo de tabla → `n_tipo_notificacion`

3. El script SHALL marcar registros migrados en `n_observacion`:
   - `"[MIGRADO_DE: nombre_tabla_origen]"`

---

## 8. Requisito: Plantillas Personalizables

**Como** administrador  
**Quiero** personalizar plantillas por empresa  
**Para** mantener identidad corporativa

### Criterios de Aceptación

1. MSCorreos SHALL usar tabla `correos.cor_plantillas`:
```sql
CREATE TABLE correos.cor_plantillas (
    id BIGSERIAL PRIMARY KEY,
    empresa VARCHAR(100) NOT NULL,
    tipo_notificacion VARCHAR(100) NOT NULL,
    asunto_template TEXT NOT NULL,
    cuerpo_html_template TEXT NOT NULL,
    cuerpo_texto_template TEXT NOT NULL,
    UNIQUE(empresa, tipo_notificacion)
);
```

2. MSCorreos SHALL sustituir variables `{{variable}}` en plantillas

3. MSCorreos SHALL usar plantilla por defecto si no existe personalizada

---

## 9. Requisitos No Funcionales

### 9.1 Performance
- Registrar notificación en < 100ms
- Consultar notificaciones en < 500ms (p95)

### 9.2 Disponibilidad
- 99.5% uptime mensual
- Health check en `/health`

### 9.3 Seguridad
- API Key authentication
- No registrar información sensible en logs
- Validar formato de emails

### 9.4 Mantenibilidad
- Código en Java 11+
- Spring Boot 2.7+
- Clean Architecture
- Cobertura de tests > 80%

---

## 10. Fuera de Alcance (No se modificará)

- ShrimpSoftServer: NO se modifica
- UtilsMail.java: NO se modifica
- EnviarCorreoServiceImpl.java: NO se modifica
- Tablas antiguas: NO se eliminan (solo lectura después de migración)
- Front-end: NO se modifica

**Solo se implementa código en MSCorreos**
