# Documento de Diseño Técnico

## Resumen

Este documento describe el diseño técnico para centralizar el envío de correos en el microservicio **MSCorreos**. ShrimpSoftServer dejará de llamar directamente a AWS SES y en su lugar hará llamadas REST HTTP a MSCorreos.

---

## 1. Stack Tecnológico

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 3.2.5 |
| Spring Data JPA | incluido en Boot 3.2.5 |
| Jakarta EE | 10 (reemplaza `javax.*` por `jakarta.*`) |
| AWS SDK | 1.12.700 |
| PostgreSQL Driver | incluido en Boot 3.2.5 |

---

## 2. Arquitectura Hexagonal (Ports & Adapters)

El microservicio se organiza en tres capas bien definidas:

```
┌─────────────────────────────────────────────────────────┐
│                    ADAPTADORES DE ENTRADA                │
│  REST Controller  │  SNS Webhook Controller              │
└──────────────────────────┬──────────────────────────────┘
                           │ usa puerto de entrada
┌──────────────────────────▼──────────────────────────────┐
│                      DOMINIO                             │
│  EnvioCorreoUseCase (port entrada)                       │
│  EnvioCorreoService (implementación)                     │
│  NotificacionRepository (port salida)                    │
│  CorreoSender (port salida)                              │
│  TemplateEngine (port salida)                            │
│  ConfiguracionEmpresaRepository (port salida)            │
│  Entidades: SolicitudCorreo, Notificacion, Adjunto       │
└──────────────────────────┬──────────────────────────────┘
                           │ implementado por
┌──────────────────────────▼──────────────────────────────┐
│                   ADAPTADORES DE SALIDA                  │
│  AwsSesAdapter  │  JpaNotificacionAdapter                │
│  HtmlTemplateAdapter  │  JpaConfiguracionAdapter         │
└─────────────────────────────────────────────────────────┘
```

### Estructura de paquetes

```
com.acosux.MSCorreos
├── application/                        ← Casos de uso (dominio)
│   ├── port/
│   │   ├── in/
│   │   │   └── EnvioCorreoUseCase.java      (interface - puerto entrada)
│   │   └── out/
│   │       ├── CorreoSenderPort.java         (interface - puerto salida AWS SES)
│   │       ├── NotificacionRepositoryPort.java (interface - puerto salida BD)
│   │       ├── TemplateEnginePort.java        (interface - puerto salida templates)
│   │       └── ConfiguracionEmpresaPort.java  (interface - puerto salida config)
│   └── service/
│       └── EnvioCorreoService.java            (implementación del caso de uso)
├── domain/                             ← Entidades y value objects del dominio
│   ├── Notificacion.java               (entidad de dominio - no JPA)
│   ├── SolicitudCorreo.java            (record Java 21)
│   ├── AdjuntoCorreo.java              (record Java 21)
│   ├── RespuestaEnvio.java             (record Java 21)
│   └── TipoNotificacion.java           (enum)
├── infrastructure/                     ← Adaptadores
│   ├── in/
│   │   └── web/
│   │       ├── EnvioCorreoController.java     (REST - entrada)
│   │       ├── SnsWebhookController.java      (SNS webhook - entrada)
│   │       └── ApiKeyFilter.java              (seguridad)
│   └── out/
│       ├── aws/
│       │   └── AwsSesAdapter.java             (implementa CorreoSenderPort)
│       ├── persistence/
│       │   ├── entity/
│       │   │   ├── NotificacionEntity.java    (entidad JPA)
│       │   │   └── ConfiguracionEmpresaEntity.java (entidad JPA)
│       │   ├── repository/
│       │   │   ├── NotificacionJpaRepository.java  (Spring Data)
│       │   │   └── ConfiguracionEmpresaJpaRepository.java
│       │   ├── JpaNotificacionAdapter.java    (implementa NotificacionRepositoryPort)
│       │   └── JpaConfiguracionAdapter.java   (implementa ConfiguracionEmpresaPort)
│       └── template/
│           └── HtmlTemplateAdapter.java       (implementa TemplateEnginePort)
└── shared/
    └── config/
        └── BeanConfig.java                    (configuración de beans Spring)
```

---

## 2. Modelo de Datos

### 2.1 Cambios en `correos.cor_notificaciones`

Agregar dos columnas nuevas:

```sql
ALTER TABLE correos.cor_notificaciones
  ADD COLUMN n_asunto TEXT,
  ADD COLUMN n_modulo VARCHAR(100);
```

| Columna | Tipo | Descripción |
|---|---|---|
| `n_secuencial` | SERIAL PK | Identificador |
| `n_destinatario` | TEXT NOT NULL | Email(s) destinatario |
| `n_fecha` | TIMESTAMP NOT NULL | Fecha del evento |
| `n_tipo` | TEXT NOT NULL | Send, Delivery, Bounce, Complaint, Open, Error |
| `n_observacion` | TEXT | Detalle adicional |
| `n_informe` | TEXT NOT NULL | JSON completo del evento |
| `n_empresa` | TEXT NOT NULL | Código de empresa |
| `n_ruc` | TEXT NOT NULL | RUC del emisor |
| `n_clave` | TEXT NOT NULL | Clave del documento |
| `n_tipo_notificacion` | TEXT NOT NULL | Descripción del tipo |
| `n_asunto` | TEXT | **NUEVO** Asunto del correo |
| `n_modulo` | VARCHAR(100) | **NUEVO** Tabla origen (para migración) |

### 2.2 Nueva tabla `correos.cor_configuracion_empresa`

```sql
CREATE TABLE correos.cor_configuracion_empresa (
  id              SERIAL PRIMARY KEY,
  emp_codigo      TEXT NOT NULL UNIQUE,
  correo_emisor   TEXT NOT NULL,
  nombre_emisor   TEXT NOT NULL,
  configuration_set TEXT NOT NULL,
  region_aws      TEXT NOT NULL DEFAULT 'us-east-1',
  url_logo        TEXT,
  html_header     TEXT,
  html_footer     TEXT,
  es_defecto      BOOLEAN NOT NULL DEFAULT false,
  usr_codigo      TEXT NOT NULL,
  usr_fecha       TIMESTAMP NOT NULL DEFAULT now()
);
```

---

## 3. API REST de MSCorreos

### 3.1 Endpoint de envío

**`POST /api/v1/correos/enviar`**

Headers requeridos:
- `Content-Type: application/json`
- `X-API-Key: {apiKey}`

#### Request — `SolicitudCorreo`

```json
{
  "empresa": "EMP001",
  "tipoNotificacion": "1",
  "destinatarios": "cliente@email.com;otro@email.com",
  "destinatariosCC": "copia@email.com",
  "asunto": "Factura Nº 001-001-000000123",
  "cuerpoTextoPlano": "Estimado cliente, adjuntamos su factura.",
  "parametrosTemplate": {
    "nombreCliente": "Juan Pérez",
    "numeroFactura": "001-001-000000123",
    "claveAcceso": "2401202401...",
    "valor": "150.00"
  },
  "adjuntos": [
    {
      "nombre": "factura.pdf",
      "contenidoBase64": "JVBERi0xLjQ...",
      "tipoMime": "application/pdf"
    }
  ],
  "claveAcceso": "2401202401...",
  "ruc": "0791807611001",
  "clave": "2024_01_000000123"
}
```

#### Response exitoso — HTTP 200

```json
{
  "messageId": "0100018d1234abcd-...",
  "estado": "ENVIADO"
}
```

#### Response error validación — HTTP 400

```json
{
  "error": "Campo obligatorio faltante: destinatarios"
}
```

#### Response error AWS — HTTP 422

```json
{
  "error": "Correo emisor no verificado en AWS SES: notificaciones@empresa.com"
}
```

---

## 4. Dominio — Records Java 21 y Entidades

### 4.1 Records del dominio (inmutables, sin setters)

```java
// SolicitudCorreo.java
public record SolicitudCorreo(
    @NotBlank String empresa,
    @NotBlank String tipoNotificacion,
    @NotBlank String destinatarios,
    String destinatariosCC,
    @NotBlank String asunto,
    String cuerpoTextoPlano,
    Map<String, Object> parametrosTemplate,
    List<AdjuntoCorreo> adjuntos,
    String claveAcceso,
    String ruc,
    String clave
) {}

// AdjuntoCorreo.java
public record AdjuntoCorreo(
    String nombre,
    String contenidoBase64,
    String tipoMime
) {}

// RespuestaEnvio.java
public record RespuestaEnvio(
    String messageId,
    String estado
) {}
```

### 4.2 Entidades JPA (en infrastructure/out/persistence/entity)

Las entidades JPA usan `jakarta.persistence.*` (Jakarta EE 10, no `javax`):

```java
@Entity
@Table(name = "cor_notificaciones", schema = "correos")
public class NotificacionEntity { ... }

@Entity
@Table(name = "cor_configuracion_empresa", schema = "correos")
public class ConfiguracionEmpresaEntity { ... }
```

### 4.3 Puertos de salida (interfaces del dominio)

```java
// CorreoSenderPort.java
public interface CorreoSenderPort {
    String enviar(SolicitudCorreo solicitud, String htmlBody,
                  ConfiguracionEmpresaEntity config) throws Exception;
}

// NotificacionRepositoryPort.java
public interface NotificacionRepositoryPort {
    void guardar(Notificacion notificacion);
}

// TemplateEnginePort.java
public interface TemplateEnginePort {
    String procesar(String tipoNotificacion, String ruc,
                    Map<String, Object> parametros);
}

// ConfiguracionEmpresaPort.java
public interface ConfiguracionEmpresaPort {
    Optional<ConfiguracionEmpresaEntity> buscarPorEmpresa(String empCodigo);
    ConfiguracionEmpresaEntity buscarDefecto();
}
```

---

## 5. Sistema de Templates HTML

### 5.1 Estructura de directorios

```
resources/
└── templates/
    └── correos/
        ├── generico/
        │   ├── header.html
        │   ├── footer.html
        │   └── base.html
        ├── tipo/
        │   ├── 1_venta_electronica.html
        │   ├── 2_compra_electronica.html
        │   ├── 3_orden_compra.html
        │   ├── 6_cuentas_cobrar.html
        │   ├── 9_rol_pagos.html
        │   └── ... (uno por TipoNotificacion)
        └── empresa/
            ├── 0791807611001/
            │   ├── header.html
            │   └── footer.html
            └── 0791755093001/
                └── header.html
```

### 5.2 Motor de templates

Se usa **sustitución simple de variables** con `String.replace()` — sin dependencias externas. Las variables usan la sintaxis `{{variable}}`. En Java 21 se aprovechan los text blocks para mayor legibilidad.

```java
// HtmlTemplateAdapter.java (implementa TemplateEnginePort)
@Component
public class HtmlTemplateAdapter implements TemplateEnginePort {

    @Override
    public String procesar(String tipoNotificacion, String ruc,
                           Map<String, Object> parametros) {
        String html = cargarTemplate(tipoNotificacion, ruc);
        for (var entry : parametros.entrySet()) {
            html = html.replace("{{" + entry.getKey() + "}}",
                                entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return html;
    }

    private String cargarTemplate(String tipoNotificacion, String ruc) {
        // 1. Busca template específico de empresa: empresa/{ruc}/{tipo}.html
        // 2. Si no existe, busca por tipo: tipo/{codigo}.html
        // 3. Si no existe, usa genérico: generico/base.html
        ...
    }
}
```

### 5.3 Variables disponibles en templates

Variables comunes disponibles en todos los templates:

| Variable | Descripción |
|---|---|
| `{{nombreCliente}}` | Nombre del receptor |
| `{{numeroComprobante}}` | Número del documento |
| `{{tipoComprobante}}` | Tipo de documento |
| `{{claveAcceso}}` | Clave de acceso SRI |
| `{{valor}}` | Valor del documento |
| `{{nombreEmisor}}` | Nombre de la empresa emisora |
| `{{rucEmisor}}` | RUC de la empresa emisora |
| `{{direccionEmisor}}` | Dirección de la empresa |
| `{{telefonoEmisor}}` | Teléfono de la empresa |

---

## 6. Integración AWS SES — `AwsSesService`

```java
// AwsSesService.java
public class AwsSesService {

    // Envío principal con adjuntos (equivalente a UtilsMail.envioCorreoPersonalizadoAmazonSES)
    public String enviarCorreo(SolicitudCorreo solicitud,
                               String htmlBody,
                               CorreosConfiguracionEmpresa config) throws Exception

    // Envío simple sin adjuntos (equivalente a UtilsMail.envioErrorAmazonSES)
    public String enviarCorreoSimple(String destinatarios, String asunto,
                                     String htmlBody, String textoPlano,
                                     CorreosConfiguracionEmpresa config)

    // Verificación de identidad en AWS SES
    public boolean esUnaEntidadVerificada(String email)
    public void verificarEmail(String email)
    public List<String> listarEntidades()

    // Tags para tracking
    private Collection<MessageTag> establecerTags(SolicitudCorreo solicitud)
}
```

Los tags de AWS SES se construyen a partir de los campos de `SolicitudCorreo`:
- `ows-tipo-notificacion` ← `tipoNotificacion`
- `ows-empresa` ← `empresa`
- `ows-ruc` ← `ruc`
- `ows-clave` ← `clave`
- `ows-clave-acceso` ← `claveAcceso`

---

## 7. Seguridad — `ApiKeyFilter`

```java
@Component
public class ApiKeyFilter implements Filter {

    @Value("${msCorreos.apiKey}")
    private String apiKeyEsperado;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // Solo aplica al endpoint de envío
        if (path.startsWith("/api/v1/correos/")) {
            String apiKey = httpRequest.getHeader("X-API-Key");
            if (apiKey == null || !apiKey.equals(apiKeyEsperado)) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
```

Configuración en `application.properties`:
```properties
msCorreos.apiKey=CAMBIAR_POR_CLAVE_SEGURA
```

---

## 8. Cliente HTTP en ShrimpSoftServer

### 8.1 Nueva clase `CorreosHttpClient`

```java
// ShrimpSoftServer - nuevo archivo
@Component
public class CorreosHttpClient {

    @Value("${msCorreos.url}")
    private String msCorreosUrl;

    @Value("${msCorreos.apiKey}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String enviar(SolicitudCorreoDTO solicitud) throws GeneralException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", apiKey);

        HttpEntity<SolicitudCorreoDTO> entity = new HttpEntity<>(solicitud, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                msCorreosUrl + "/api/v1/correos/enviar", entity, String.class);
            return response.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new GeneralException("Error al enviar correo: " + e.getResponseBodyAsString());
        }
    }
}
```

Configuración en `application.properties` de ShrimpSoftServer:
```properties
msCorreos.url=http://localhost:8081/MSCorreos
msCorreos.apiKey=CAMBIAR_POR_CLAVE_SEGURA
```

### 8.2 Adaptación de `EnviarCorreoServiceImpl`

Cada método actual que llama a `UtilsMail` se reemplaza por una construcción de `SolicitudCorreoDTO` y una llamada a `CorreosHttpClient.enviar()`. La firma pública de `EnviarCorreoService` no cambia.

Ejemplo de migración:

```java
// ANTES
public String enviarComprobantesElectronicos(SisEmailComprobanteElectronicoTO to,
                                              List<File> adjuntos) {
    // ... construye HTML ...
    return UtilsMail.envioCorreoPersonalizadoAmazonSES(to, destinatarios,
                                                        asunto, html, "", adjuntos, notif);
}

// DESPUÉS
public String enviarComprobantesElectronicos(SisEmailComprobanteElectronicoTO to,
                                              List<File> adjuntos) {
    SolicitudCorreoDTO solicitud = new SolicitudCorreoDTO();
    solicitud.setEmpresa(to.getEmpresa());
    solicitud.setTipoNotificacion(to.getTipoComprobante());
    solicitud.setDestinatarios(to.getMailReceptor());
    solicitud.setAsunto(to.getTipoComprobante() + " Nº " + to.getNumeroComprobante());
    solicitud.setRuc(to.getRucEmisor());
    solicitud.setClave(to.getPeriodo() + "_" + to.getMotivo() + "_" + to.getNumero());
    solicitud.setClaveAcceso(to.getClaveAcceso());
    // Parámetros para el template
    Map<String, Object> params = new HashMap<>();
    params.put("nombreCliente", to.getNombreReceptor());
    params.put("numeroComprobante", to.getNumeroComprobante());
    params.put("tipoComprobante", to.getTipoComprobante());
    params.put("claveAcceso", to.getClaveAcceso());
    params.put("valor", to.getValor());
    solicitud.setParametrosTemplate(params);
    // Adjuntos en Base64
    solicitud.setAdjuntos(convertirAdjuntos(adjuntos));
    return correosHttpClient.enviar(solicitud);
}
```

---

## 9. Nuevos Tipos de Notificación

Agregar al enum `TipoNotificacion` en MSCorreos:

| Código | Constante | Descripción |
|---|---|---|
| 12 | `NOTIFICAR_PAGO_PROVEEDOR` | Pagos a proveedor |
| 13 | `NOTIFICAR_ANTICIPO_PROVEEDOR` | Anticipos a proveedor |
| 14 | `NOTIFICAR_BENEFICIO_XIII` | XIII Sueldo |
| 15 | `NOTIFICAR_BENEFICIO_XIV` | XIV Sueldo |
| 16 | `NOTIFICAR_BENEFICIO_UTILIDADES` | Utilidades |
| 17 | `NOTIFICAR_ERROR_SISTEMA` | Errores del sistema |
| 18 | `NOTIFICAR_TICKET_SOPORTE` | Tickets de soporte |
| 19 | `NOTIFICAR_DOCUMENTO_NO_AUTORIZADO` | Documentos no autorizados |
| 20 | `NOTIFICAR_PROVEEDOR_IMB` | Proveedor IMB |
| 21 | `NOTIFICAR_ANULACION_VENTA` | Anulación de venta |
| 22 | `NOTIFICAR_ANULACION_RETENCION_COMPRA` | Anulación retención compra |
| 23 | `NOTIFICAR_ORDEN_COMPRA_REGISTRADOR` | Orden de compra a registrador |

---

## 10. Script SQL de Migración

El script se ubica en `sql/updates/` y sigue esta estructura:

```sql
BEGIN;

-- 1. Agregar columnas nuevas si no existen
ALTER TABLE correos.cor_notificaciones
  ADD COLUMN IF NOT EXISTS n_asunto TEXT,
  ADD COLUMN IF NOT EXISTS n_modulo VARCHAR(100);

-- 2. Migración idempotente por tabla
-- Usa n_modulo + secuencial_origen como clave de deduplicación

-- 2.1 Ventas electrónicas
INSERT INTO correos.cor_notificaciones
  (n_destinatario, n_fecha, n_tipo, n_observacion, n_informe,
   n_empresa, n_ruc, n_clave, n_tipo_notificacion, n_modulo)
SELECT
  e_destinatario, e_fecha, e_tipo, e_observacion, e_informe,
  vta_empresa, '', vta_periodo || '_' || vta_motivo || '_' || vta_numero,
  'VENTAS ELECTRÓNICAS EMITIDAS',
  'anexo.anx_venta_electronica_notificaciones'
FROM anexo.anx_venta_electronica_notificaciones src
WHERE NOT EXISTS (
  SELECT 1 FROM correos.cor_notificaciones dest
  WHERE dest.n_modulo = 'anexo.anx_venta_electronica_notificaciones'
    AND dest.n_clave = src.vta_periodo || '_' || src.vta_motivo || '_' || src.vta_numero
    AND dest.n_destinatario = src.e_destinatario
    AND dest.n_fecha = src.e_fecha
);

-- (se repite para las 8 tablas restantes con su mapeo correspondiente)

-- 3. Reporte final
DO $$
BEGIN
  RAISE NOTICE 'Migración completada.';
  RAISE NOTICE 'cor_notificaciones total: %',
    (SELECT COUNT(*) FROM correos.cor_notificaciones);
END $$;

COMMIT;
```

### Mapeo de tablas origen a `n_tipo_notificacion`

| Tabla origen | `n_tipo_notificacion` |
|---|---|
| `anx_venta_electronica_notificaciones` | `VENTAS ELECTRÓNICAS EMITIDAS` |
| `anx_compra_electronica_notificaciones` | `COMPRAS ELECTRÓNICAS EMITIDAS` |
| `anx_guia_remision_electronica_notificaciones` | `GUIA DE REMISIÓN EMITIDAS` |
| `anx_liquidacion_compras_electronica_notificaciones` | `LIQUIDACIÓN DE COMPRA EMITIDAS` |
| `car_pagos_notificaciones` | `NOTIFICAR A PROVEEDOR DE PAGO` |
| `car_pagos_anticipos_notificaciones` | `NOTIFICAR ANTICIPO PROVEEDOR` |
| `rh_rol_pago_notificaciones` | `ROLES DE PAGO` |
| `inv_pedidos_orden_compra_notificaciones` | `NOTIFICAR A PROVEEDOR DE ORDEN DE COMPRA` |
| `inv_cliente_notificaciones` | `NOTIFICACIONES A CLIENTES` |

---

## 11. Flujo de Envío — Secuencia

```
ShrimpSoftServer                MSCorreos                    AWS SES
      │                              │                           │
      │  POST /api/v1/correos/enviar │                           │
      │  {SolicitudCorreo}           │                           │
      │─────────────────────────────►│                           │
      │                              │                           │
      │                    ApiKeyFilter valida                   │
      │                    ConfiguracionEmpresaService           │
      │                    TemplateService carga HTML            │
      │                    AwsSesService construye MIME          │
      │                              │  SendRawEmail             │
      │                              │──────────────────────────►│
      │                              │  messageId                │
      │                              │◄──────────────────────────│
      │                    CorreosNotificacionDao.insertar()     │
      │  HTTP 200 {messageId}        │                           │
      │◄─────────────────────────────│                           │
      │                              │                           │
      │                              │   (más tarde)             │
      │                              │◄── SNS webhook ───────────│
      │                    CorreosNotificacionesServiceImpl      │
      │                    inserta Delivery/Bounce/etc           │
```
