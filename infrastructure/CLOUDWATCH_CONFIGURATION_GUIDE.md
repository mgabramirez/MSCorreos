# Guía de Configuración de CloudWatch para MSCorreos

Esta guía describe la configuración completa de Amazon CloudWatch para el sistema de notificaciones MSCorreos, incluyendo log groups, métricas personalizadas, alarmas y dashboards.

## Tabla de Contenidos

1. [Requisitos Previos](#requisitos-previos)
2. [Arquitectura de Observabilidad](#arquitectura-de-observabilidad)
3. [Despliegue con CloudFormation](#despliegue-con-cloudformation)
4. [Despliegue con Terraform](#despliegue-con-terraform)
5. [Log Groups](#log-groups)
6. [Métricas Personalizadas](#métricas-personalizadas)
7. [Alarmas Configuradas](#alarmas-configuradas)
8. [Dashboard](#dashboard)
9. [Integración con la Aplicación](#integración-con-la-aplicación)
10. [Consulta de Logs](#consulta-de-logs)
11. [Troubleshooting](#troubleshooting)

## Requisitos Previos

### Herramientas Necesarias

- **AWS CLI** (versión 2.x o superior)
- **CloudFormation** o **Terraform** (versión 1.0+)
- **Permisos IAM** necesarios:
  - `logs:*`
  - `cloudwatch:*`
  - `sns:*`
  - `cloudformation:*` (si usa CloudFormation)

### Verificar Instalación

```bash
aws --version
aws cloudformation validate-template --help
```

## Arquitectura de Observabilidad

```
┌─────────────────────────────────────────────────────────────────┐
│                      MSCorreos Application                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ SQS Consumer │  │Email Service │  │ SNS Listener │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                  │                  │                   │
│         └──────────────────┴──────────────────┘                  │
│                            │                                      │
└────────────────────────────┼──────────────────────────────────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │   CloudWatch Logs    │
                  │  (5 Log Groups)      │
                  │  Retención: 30 días  │
                  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │   Metric Filters     │
                  │  (Extraer métricas)  │
                  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │ CloudWatch Metrics   │
                  │  (Namespace: MSCorreos)│
                  └──────────┬───────────┘
                             │
                ┌────────────┴────────────┐
                ▼                         ▼
     ┌──────────────────┐      ┌──────────────────┐
     │ CloudWatch Alarms│      │ CloudWatch       │
     │  (6 alarmas)     │      │ Dashboard        │
     └────────┬─────────┘      └──────────────────┘
              │
              ▼
     ┌──────────────────┐
     │   SNS Topic      │
     │  (Notificaciones)│
     └────────┬─────────┘
              │
              ▼
     ┌──────────────────┐
     │   Email          │
     │  (Administrador) │
     └──────────────────┘
```

## Despliegue con CloudFormation

### Paso 1: Validar Template

```bash
cd MSCorreos/infrastructure/cloudformation
aws cloudformation validate-template \
    --template-body file://cloudwatch.yaml \
    --region us-east-1
```

### Paso 2: Desplegar Stack

#### Sin notificaciones por email:
```bash
cd ../scripts
chmod +x deploy-cloudwatch.sh
./deploy-cloudwatch.sh dev
```

#### Con notificaciones por email:
```bash
./deploy-cloudwatch.sh dev admin@example.com
```

### Paso 3: Confirmar Suscripción SNS

Si configuró un email, revise su bandeja de entrada y confirme la suscripción al SNS Topic.

### Paso 4: Verificar Despliegue

```bash
# Ver estado del stack
aws cloudformation describe-stacks \
    --stack-name mscorreos-cloudwatch-dev \
    --region us-east-1

# Ver outputs
aws cloudformation describe-stacks \
    --stack-name mscorreos-cloudwatch-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs'
```

## Despliegue con Terraform

### Paso 1: Inicializar Terraform

```bash
cd MSCorreos/infrastructure/terraform
terraform init
```

### Paso 2: Configurar Variables

Editar `terraform.tfvars`:
```hcl
environment = "dev"
aws_region  = "us-east-1"
alarm_email = "admin@example.com"  # Opcional
```

### Paso 3: Planificar y Aplicar

```bash
terraform plan
terraform apply
```

### Paso 4: Obtener Outputs

```bash
terraform output
```

## Log Groups

### Log Groups Creados

| Log Group | Propósito | Retención |
|-----------|-----------|-----------|
| `/aws/mscorreos/{env}` | Logs generales de la aplicación | 30 días |
| `/aws/mscorreos/{env}/sqs-consumer` | Logs del consumidor SQS | 30 días |
| `/aws/mscorreos/{env}/email-service` | Logs del servicio de email | 30 días |
| `/aws/mscorreos/{env}/sns-listener` | Logs del listener SNS | 30 días |
| `/aws/mscorreos/{env}/blacklist-service` | Logs del servicio de lista negra | 30 días |

### Estructura de Logs

Los logs deben seguir este formato estructurado:

```json
{
  "timestamp": "2025-01-09T10:30:45.123Z",
  "level": "INFO",
  "message": "Email sent successfully",
  "trace_id": "abc123",
  "empresa": "ACOSUX",
  "tipo_notificacion": "NOTIFICAR_VENTA_ELECTRONICA_EMITIDA",
  "destinatario": "cliente@example.com"
}
```

## Métricas Personalizadas

### Namespace: MSCorreos

Todas las métricas personalizadas se publican en el namespace `MSCorreos`.

### Métricas Disponibles

| Métrica | Descripción | Unidad | Fuente |
|---------|-------------|--------|--------|
| `CorreosEnviados` | Total de correos enviados exitosamente | Count | Metric Filter |
| `CorreosFallidos` | Total de correos que fallaron al enviar | Count | Metric Filter |
| `CorreosBloqueados` | Total de correos bloqueados por lista negra | Count | Metric Filter |
| `ErrorCount` | Total de errores en la aplicación | Count | Metric Filter |
| `TiempoProcesamiento` | Tiempo de procesamiento de mensajes | Milliseconds | Aplicación |
| `TotalCorreosListaNegra` | Total de correos en lista negra | Count | Aplicación |

### Publicar Métricas desde la Aplicación

```java
@Service
public class CloudWatchMetricsService {
    private final CloudWatchClient cloudWatchClient;
    private final String namespace = "MSCorreos";
    
    public void registrarTiempoProcesamiento(long millis) {
        MetricDatum datum = MetricDatum.builder()
            .metricName("TiempoProcesamiento")
            .value((double) millis)
            .unit(StandardUnit.MILLISECONDS)
            .timestamp(Instant.now())
            .dimensions(
                Dimension.builder()
                    .name("Environment")
                    .value(environment)
                    .build()
            )
            .build();
        
        PutMetricDataRequest request = PutMetricDataRequest.builder()
            .namespace(namespace)
            .metricData(datum)
            .build();
        
        cloudWatchClient.putMetricData(request);
    }
    
    public void registrarTotalListaNegra(int total) {
        MetricDatum datum = MetricDatum.builder()
            .metricName("TotalCorreosListaNegra")
            .value((double) total)
            .unit(StandardUnit.COUNT)
            .timestamp(Instant.now())
            .dimensions(
                Dimension.builder()
                    .name("Environment")
                    .value(environment)
                    .build()
            )
            .build();
        
        PutMetricDataRequest request = PutMetricDataRequest.builder()
            .namespace(namespace)
            .metricData(datum)
            .build();
        
        cloudWatchClient.putMetricData(request);
    }
}
```

## Alarmas Configuradas

### 1. Alarma de Tasa de Errores (Requirement 8.4)

**Nombre**: `mscorreos-error-rate-{env}`

**Condición**: Se activa cuando hay más de 5 errores en 5 minutos

**Severidad**: High

**Acción**: Notificación SNS

```bash
# Ver estado de la alarma
aws cloudwatch describe-alarms \
    --alarm-names mscorreos-error-rate-dev \
    --region us-east-1
```

### 2. Alarma de Tiempo de Procesamiento (Requirement 8.5)

**Nombre**: `mscorreos-processing-time-{env}`

**Condición**: Se activa cuando el tiempo promedio de procesamiento supera 5 segundos

**Severidad**: Medium

**Acción**: Notificación SNS

### 3. Alarma de Tamaño de Lista Negra (Requirement 16.15)

**Nombre**: `mscorreos-blacklist-size-{env}`

**Condición**: Se activa cuando la lista negra supera 1000 correos

**Severidad**: High

**Acción**: Notificación SNS

### 4. Alarma de DLQ (Requirement 8.3)

**Nombre**: `mscorreos-dlq-high-messages-{env}`

**Condición**: Se activa cuando la DLQ contiene más de 10 mensajes

**Severidad**: Critical

**Acción**: Notificación SNS

### 5. Alarma de Fallos Consecutivos

**Nombre**: `mscorreos-consecutive-failures-{env}`

**Condición**: Se activa cuando hay más de 10 correos fallidos en 3 minutos consecutivos

**Severidad**: High

**Acción**: Notificación SNS

### 6. Alarma de Alta Tasa de Bloqueos

**Nombre**: `mscorreos-high-blocked-emails-{env}`

**Condición**: Se activa cuando se bloquean más de 50 correos en 5 minutos

**Severidad**: Medium

**Acción**: Notificación SNS

### Gestionar Alarmas

```bash
# Listar todas las alarmas
aws cloudwatch describe-alarms \
    --alarm-name-prefix mscorreos- \
    --region us-east-1

# Ver historial de una alarma
aws cloudwatch describe-alarm-history \
    --alarm-name mscorreos-error-rate-dev \
    --region us-east-1

# Deshabilitar una alarma temporalmente
aws cloudwatch disable-alarm-actions \
    --alarm-names mscorreos-error-rate-dev \
    --region us-east-1

# Habilitar una alarma
aws cloudwatch enable-alarm-actions \
    --alarm-names mscorreos-error-rate-dev \
    --region us-east-1
```

## Dashboard

### Acceder al Dashboard

El dashboard `MSCorreos-{env}` se crea automáticamente y contiene:

1. **Gráfico de Correos**: Enviados, Fallidos, Bloqueados
2. **Gráfico de Tiempo de Procesamiento**: Promedio y Máximo
3. **Gráfico de Mensajes en Colas SQS**: Standard, FIFO, DLQ
4. **Gráfico de Errores**: Total de errores de aplicación
5. **Métrica de Lista Negra**: Total de correos bloqueados
6. **Logs de Errores**: Últimos 20 errores

### URL del Dashboard

```bash
# Obtener URL del dashboard
aws cloudformation describe-stacks \
    --stack-name mscorreos-cloudwatch-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`DashboardURL`].OutputValue' \
    --output text
```

O acceder directamente:
```
https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=MSCorreos-dev
```

## Integración con la Aplicación

### Configuración de Spring Boot

#### application.yml

```yaml
logging:
  level:
    root: INFO
    com.acosux.mscorreos: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

cloud:
  aws:
    cloudwatch:
      namespace: MSCorreos
      enabled: true
    region:
      static: us-east-1
```

#### Dependencias Maven

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>cloudwatch</artifactId>
    <version>2.20.0</version>
</dependency>

<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>cloudwatchlogs</artifactId>
    <version>2.20.0</version>
</dependency>
```

### Configuración de Logback

#### logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="environment" source="spring.profiles.active"/>
    <springProperty scope="context" name="logGroup" source="cloud.aws.cloudwatch.log-group" 
                    defaultValue="/aws/mscorreos/${environment}"/>
    
    <!-- Console Appender -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- CloudWatch Appender -->
    <appender name="CLOUDWATCH" class="ca.pjer.logback.AwsLogsAppender">
        <logGroupName>${logGroup}</logGroupName>
        <logStreamName>mscorreos-${HOSTNAME}-${environment}</logStreamName>
        <layout>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </layout>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="CLOUDWATCH"/>
    </root>
</configuration>
```

### Logging Estructurado

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    
    public void enviarCorreo(EventoNotificacion evento) {
        // Agregar contexto al MDC
        MDC.put("trace_id", UUID.randomUUID().toString());
        MDC.put("empresa", evento.getEmpresa());
        MDC.put("tipo_notificacion", evento.getTipoNotificacion());
        MDC.put("destinatario", evento.getDestinatarios().get(0));
        
        try {
            log.info("Email sent successfully");
            // Lógica de envío
        } catch (Exception e) {
            log.error("Failed to send email", e);
        } finally {
            MDC.clear();
        }
    }
}
```

## Consulta de Logs

### CloudWatch Logs Insights

#### Ver últimos errores

```sql
fields @timestamp, level, message, trace_id, empresa
| filter level = "ERROR"
| sort @timestamp desc
| limit 20
```

#### Contar correos enviados por empresa

```sql
fields @timestamp, empresa
| filter message = "Email sent successfully"
| stats count() by empresa
| sort count desc
```

#### Tiempo promedio de procesamiento

```sql
fields @timestamp, TiempoProcesamiento
| filter TiempoProcesamiento > 0
| stats avg(TiempoProcesamiento) as avg_time, max(TiempoProcesamiento) as max_time
```

#### Correos bloqueados por lista negra

```sql
fields @timestamp, destinatario, motivo
| filter message = "Email blocked by blacklist"
| sort @timestamp desc
| limit 50
```

### AWS CLI

```bash
# Ver logs en tiempo real
aws logs tail /aws/mscorreos/dev --follow

# Buscar errores en las últimas 2 horas
aws logs filter-log-events \
    --log-group-name /aws/mscorreos/dev \
    --filter-pattern "ERROR" \
    --start-time $(date -u -d '2 hours ago' +%s)000 \
    --region us-east-1

# Exportar logs a S3
aws logs create-export-task \
    --log-group-name /aws/mscorreos/dev \
    --from $(date -u -d '7 days ago' +%s)000 \
    --to $(date -u +%s)000 \
    --destination mscorreos-logs-export \
    --region us-east-1
```

## Troubleshooting

### Problema: Logs no aparecen en CloudWatch

**Solución**:
1. Verificar que el Log Group existe:
```bash
aws logs describe-log-groups \
    --log-group-name-prefix /aws/mscorreos \
    --region us-east-1
```

2. Verificar permisos IAM del rol de la aplicación:
```json
{
  "Effect": "Allow",
  "Action": [
    "logs:CreateLogGroup",
    "logs:CreateLogStream",
    "logs:PutLogEvents"
  ],
  "Resource": "arn:aws:logs:*:*:*"
}
```

3. Verificar configuración de logback en la aplicación

### Problema: Métricas no se publican

**Solución**:
1. Verificar que CloudWatchMetricsService está configurado correctamente
2. Verificar permisos IAM:
```json
{
  "Effect": "Allow",
  "Action": [
    "cloudwatch:PutMetricData"
  ],
  "Resource": "*"
}
```

3. Verificar que el namespace es correcto: `MSCorreos`

### Problema: Alarmas no se activan

**Solución**:
1. Verificar que las métricas tienen datos:
```bash
aws cloudwatch get-metric-statistics \
    --namespace MSCorreos \
    --metric-name ErrorCount \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Sum \
    --region us-east-1
```

2. Verificar estado de la alarma:
```bash
aws cloudwatch describe-alarms \
    --alarm-names mscorreos-error-rate-dev \
    --region us-east-1
```

3. Verificar que el SNS Topic tiene suscriptores confirmados

### Problema: No recibo notificaciones por email

**Solución**:
1. Verificar que confirmó la suscripción SNS
2. Revisar carpeta de spam
3. Verificar suscripciones del topic:
```bash
aws sns list-subscriptions-by-topic \
    --topic-arn arn:aws:sns:us-east-1:ACCOUNT_ID:mscorreos-alarms-dev \
    --region us-east-1
```

## Costos Estimados

### CloudWatch Logs
- Primeros 5 GB/mes: $0.50/GB
- Retención: Sin costo adicional
- **Estimado**: $2-5 USD/mes

### CloudWatch Metrics
- Primeras 10 métricas personalizadas: GRATIS
- Después: $0.30 por métrica/mes
- **Estimado**: $0-2 USD/mes

### CloudWatch Alarms
- $0.10 por alarma/mes
- 6 alarmas = $0.60/mes
- **Estimado**: $0.60 USD/mes

### CloudWatch Dashboard
- Primeros 3 dashboards: GRATIS
- **Estimado**: $0 USD/mes

### Total Estimado
**$3-8 USD/mes** para ambiente de producción

## Referencias

- [AWS CloudWatch Documentation](https://docs.aws.amazon.com/cloudwatch/)
- [CloudWatch Logs Insights Query Syntax](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/CWL_QuerySyntax.html)
- [CloudWatch Metrics](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/working_with_metrics.html)
- [CloudWatch Alarms](https://docs.aws.amazon.com/AmazonCloudWatch/latest/monitoring/AlarmThatSendsEmail.html)
- [Requirements Document](../MSCorreos/.kiro/specs/refactorizacion-sistema-notificaciones-email/requirements.md)
- [Design Document](../MSCorreos/.kiro/specs/refactorizacion-sistema-notificaciones-email/design.md)

## Soporte

Para problemas o preguntas, contactar al equipo de desarrollo de MSCorreos.
