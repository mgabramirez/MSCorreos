# CloudWatch - Referencia Rápida

Comandos y configuraciones esenciales para CloudWatch en MSCorreos.

## Despliegue Rápido

```bash
# CloudFormation (sin email)
cd MSCorreos/infrastructure/scripts
./deploy-cloudwatch.sh dev

# CloudFormation (con email)
./deploy-cloudwatch.sh dev admin@example.com

# Terraform
cd MSCorreos/infrastructure/terraform
terraform init
terraform apply
```

## Log Groups

| Log Group | Propósito |
|-----------|-----------|
| `/aws/mscorreos/{env}` | Logs generales |
| `/aws/mscorreos/{env}/sqs-consumer` | Consumidor SQS |
| `/aws/mscorreos/{env}/email-service` | Servicio de email |
| `/aws/mscorreos/{env}/sns-listener` | Listener SNS |
| `/aws/mscorreos/{env}/blacklist-service` | Lista negra |

## Métricas (Namespace: MSCorreos)

| Métrica | Descripción | Unidad |
|---------|-------------|--------|
| `CorreosEnviados` | Correos enviados | Count |
| `CorreosFallidos` | Correos fallidos | Count |
| `CorreosBloqueados` | Correos bloqueados | Count |
| `ErrorCount` | Errores totales | Count |
| `TiempoProcesamiento` | Tiempo de procesamiento | Milliseconds |
| `TotalCorreosListaNegra` | Total en lista negra | Count |

## Alarmas

| Alarma | Condición | Severidad |
|--------|-----------|-----------|
| `mscorreos-error-rate-{env}` | Errores > 5 en 5 min | High |
| `mscorreos-processing-time-{env}` | Tiempo > 5s | Medium |
| `mscorreos-blacklist-size-{env}` | Lista negra > 1000 | High |
| `mscorreos-dlq-high-messages-{env}` | DLQ > 10 mensajes | Critical |
| `mscorreos-consecutive-failures-{env}` | 10+ fallos en 3 min | High |
| `mscorreos-high-blocked-emails-{env}` | 50+ bloqueados en 5 min | Medium |

## Comandos Útiles

### Ver Logs en Tiempo Real
```bash
aws logs tail /aws/mscorreos/dev --follow
```

### Buscar Errores
```bash
aws logs filter-log-events \
    --log-group-name /aws/mscorreos/dev \
    --filter-pattern "ERROR" \
    --region us-east-1
```

### Ver Estado de Alarmas
```bash
aws cloudwatch describe-alarms \
    --alarm-name-prefix mscorreos- \
    --region us-east-1
```

### Ver Métricas
```bash
aws cloudwatch get-metric-statistics \
    --namespace MSCorreos \
    --metric-name CorreosEnviados \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Sum \
    --region us-east-1
```

### Acceder al Dashboard
```
https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=MSCorreos-dev
```

## Queries de CloudWatch Logs Insights

### Últimos Errores
```sql
fields @timestamp, level, message, trace_id
| filter level = "ERROR"
| sort @timestamp desc
| limit 20
```

### Correos por Empresa
```sql
fields @timestamp, empresa
| filter message = "Email sent successfully"
| stats count() by empresa
```

### Correos Bloqueados
```sql
fields @timestamp, destinatario, motivo
| filter message = "Email blocked by blacklist"
| sort @timestamp desc
```

## Integración con Spring Boot

### Dependencias Maven
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>cloudwatch</artifactId>
    <version>2.20.0</version>
</dependency>
```

### Publicar Métrica
```java
@Service
public class CloudWatchMetricsService {
    private final CloudWatchClient cloudWatchClient;
    
    public void registrarTiempoProcesamiento(long millis) {
        MetricDatum datum = MetricDatum.builder()
            .metricName("TiempoProcesamiento")
            .value((double) millis)
            .unit(StandardUnit.MILLISECONDS)
            .timestamp(Instant.now())
            .build();
        
        PutMetricDataRequest request = PutMetricDataRequest.builder()
            .namespace("MSCorreos")
            .metricData(datum)
            .build();
        
        cloudWatchClient.putMetricData(request);
    }
}
```

### Logging Estructurado
```java
import org.slf4j.MDC;

MDC.put("trace_id", UUID.randomUUID().toString());
MDC.put("empresa", evento.getEmpresa());
log.info("Email sent successfully");
MDC.clear();
```

## Permisos IAM Necesarios

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "cloudwatch:PutMetricData"
      ],
      "Resource": "*"
    }
  ]
}
```

## Troubleshooting Rápido

### Logs no aparecen
1. Verificar Log Group existe
2. Verificar permisos IAM
3. Verificar configuración logback

### Métricas no se publican
1. Verificar namespace: `MSCorreos`
2. Verificar permisos IAM
3. Verificar CloudWatchMetricsService

### Alarmas no se activan
1. Verificar métricas tienen datos
2. Verificar estado de alarma
3. Verificar SNS Topic

### No recibo emails
1. Confirmar suscripción SNS
2. Revisar spam
3. Verificar suscripciones del topic

## Costos Estimados

- **CloudWatch Logs**: $2-5 USD/mes
- **CloudWatch Metrics**: $0-2 USD/mes
- **CloudWatch Alarms**: $0.60 USD/mes
- **Total**: $3-8 USD/mes

## Enlaces Útiles

- [Guía Completa](./CLOUDWATCH_CONFIGURATION_GUIDE.md)
- [AWS CloudWatch Docs](https://docs.aws.amazon.com/cloudwatch/)
- [Logs Insights Query Syntax](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/CWL_QuerySyntax.html)
