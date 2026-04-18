# Amazon SNS - Referencia Rápida

## Despliegue Rápido

```bash
cd MSCorreos/infrastructure/scripts
chmod +x deploy-sns.sh
./deploy-sns.sh dev https://mscorreos-dev.acosux.com/sns/notifications
```

## Comandos Esenciales

### Obtener ARN del Topic

```bash
aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`TopicArn`].OutputValue' \
    --output text
```

### Verificar Estado de Suscripción

```bash
SUBSCRIPTION_ARN=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`SubscriptionArn`].OutputValue' \
    --output text)

aws sns get-subscription-attributes \
    --subscription-arn "$SUBSCRIPTION_ARN" \
    --region us-east-1
```

### Publicar Mensaje de Prueba

```bash
TOPIC_ARN=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`TopicArn`].OutputValue' \
    --output text)

aws sns publish \
    --topic-arn "$TOPIC_ARN" \
    --message "Test message" \
    --region us-east-1
```

### Ver Métricas

```bash
# Mensajes publicados
aws cloudwatch get-metric-statistics \
    --namespace AWS/SNS \
    --metric-name NumberOfMessagesPublished \
    --dimensions Name=TopicName,Value=mscorreos-tracking-events-dev \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Sum \
    --region us-east-1

# Notificaciones fallidas
aws cloudwatch get-metric-statistics \
    --namespace AWS/SNS \
    --metric-name NumberOfNotificationsFailed \
    --dimensions Name=TopicName,Value=mscorreos-tracking-events-dev \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Sum \
    --region us-east-1
```


## Recursos por Ambiente

### Desarrollo
- **Stack**: `mscorreos-sns-tracking-dev`
- **Topic**: `mscorreos-tracking-events-dev`
- **Endpoint**: `https://mscorreos-dev.acosux.com/sns/notifications`
- **Alarma**: `mscorreos-sns-failed-notifications-dev`

### Testing
- **Stack**: `mscorreos-sns-tracking-test`
- **Topic**: `mscorreos-tracking-events-test`
- **Endpoint**: `https://mscorreos-test.acosux.com/sns/notifications`
- **Alarma**: `mscorreos-sns-failed-notifications-test`

### Producción
- **Stack**: `mscorreos-sns-tracking-prod`
- **Topic**: `mscorreos-tracking-events-prod`
- **Endpoint**: `https://mscorreos.acosux.com/sns/notifications`
- **Alarma**: `mscorreos-sns-failed-notifications-prod`

## Estructura de Mensajes

### Confirmación de Suscripción

```json
{
  "Type": "SubscriptionConfirmation",
  "MessageId": "...",
  "Token": "...",
  "TopicArn": "arn:aws:sns:us-east-1:...:mscorreos-tracking-events-dev",
  "SubscribeURL": "https://sns.us-east-1.amazonaws.com/?Action=ConfirmSubscription&..."
}
```

**Acción requerida**: Hacer GET al `SubscribeURL`

### Notificación de Evento

```json
{
  "Type": "Notification",
  "MessageId": "...",
  "TopicArn": "arn:aws:sns:us-east-1:...:mscorreos-tracking-events-dev",
  "Message": "{\"eventType\":\"Delivery\",\"mail\":{...},\"delivery\":{...}}",
  "Timestamp": "2025-01-09T12:00:00.000Z"
}
```

**Acción requerida**: Parsear campo `Message` y procesar evento

## Tipos de Eventos de Tracking

| Evento | Descripción | Acción en MSCorreos |
|--------|-------------|---------------------|
| **Send** | Correo enviado a SES | Registrar en BD |
| **Delivery** | Correo entregado | Registrar en BD |
| **Open** | Correo abierto | Registrar en BD |
| **Bounce** (Permanent) | Rebote permanente | Registrar + Agregar a lista negra |
| **Bounce** (Transient) | Rebote temporal | Registrar + Contar soft bounces |
| **Complaint** | Marcado como spam | Registrar + Agregar a lista negra |
| **Reject** | SES rechazó el correo | Registrar en BD |
| **RenderingFailure** | Error en plantilla | Registrar en BD |

## Checklist de Configuración

- [ ] Stack de CloudFormation desplegado
- [ ] Topic SNS creado
- [ ] Política de acceso configurada para SES
- [ ] Suscripción HTTPS creada
- [ ] Suscripción confirmada desde MSCorreos
- [ ] Alarma de CloudWatch activa
- [ ] SES Configuration Set apunta al topic
- [ ] Endpoint MSCorreos implementado
- [ ] Validación de firma SNS implementada
- [ ] Procesamiento de eventos implementado
- [ ] Prueba de flujo completo realizada


## Configuración de Variables de Entorno

```bash
# .env o application.yml
AWS_REGION=us-east-1
SNS_TOPIC_ARN=arn:aws:sns:us-east-1:123456789012:mscorreos-tracking-events-dev
SNS_ENDPOINT=/sns/notifications
```

## Implementación Básica en MSCorreos

```java
@RestController
@RequestMapping("/sns")
public class SNSListener {
    
    @PostMapping("/notifications")
    public ResponseEntity<Void> receiveNotification(@RequestBody String payload) {
        Map<String, Object> message = parseJson(payload);
        String type = (String) message.get("Type");
        
        if ("SubscriptionConfirmation".equals(type)) {
            String subscribeUrl = (String) message.get("SubscribeURL");
            confirmarSuscripcion(subscribeUrl);
            return ResponseEntity.ok().build();
        }
        
        if ("Notification".equals(type)) {
            String eventJson = (String) message.get("Message");
            procesarEvento(eventJson);
            return ResponseEntity.ok().build();
        }
        
        return ResponseEntity.badRequest().build();
    }
}
```

## Troubleshooting Rápido

### Suscripción no se confirma
```bash
# Verificar que endpoint está accesible
curl -X POST https://mscorreos-dev.acosux.com/sns/notifications

# Reenviar confirmación
aws sns subscribe \
    --topic-arn "$TOPIC_ARN" \
    --protocol https \
    --notification-endpoint https://mscorreos-dev.acosux.com/sns/notifications \
    --region us-east-1
```

### Mensajes no llegan
```bash
# Verificar estado de suscripción
aws sns get-subscription-attributes \
    --subscription-arn "$SUBSCRIPTION_ARN" \
    --region us-east-1

# Ver métricas de fallos
aws cloudwatch get-metric-statistics \
    --namespace AWS/SNS \
    --metric-name NumberOfNotificationsFailed \
    --dimensions Name=TopicName,Value=mscorreos-tracking-events-dev \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Sum \
    --region us-east-1
```

### Firma inválida
```xml
<!-- Agregar dependencia -->
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-java-sdk-sns</artifactId>
    <version>1.12.529</version>
</dependency>
```

## Límites y Cuotas

| Recurso | Límite | Notas |
|---------|--------|-------|
| Mensajes/segundo | 30,000 | Por topic |
| Tamaño de mensaje | 256 KB | Máximo |
| Suscripciones por topic | 12,500,000 | Soft limit |
| Filtros por suscripción | 5 | Máximo |
| Reintentos de entrega | 3 | Para HTTPS |
| Timeout de entrega | 15 segundos | Para HTTPS |

## Costos Estimados

- **Primeros 1,000 mensajes/mes**: GRATIS
- **Después**: $0.50 por millón de mensajes
- **Entregas HTTPS**: $0.60 por millón
- **Alarma CloudWatch**: $0.10/mes

**Ejemplo**: 300,000 correos/mes × 3 eventos = 900,000 eventos
- Costo: ~$1.09 USD/mes

## Referencias Rápidas

- [Guía Completa](./SNS_CONFIGURATION_GUIDE.md)
- [Documentación AWS SNS](https://docs.aws.amazon.com/sns/)
- [Validación de Firma](https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html)
- [Métricas CloudWatch](https://docs.aws.amazon.com/sns/latest/dg/sns-monitoring-using-cloudwatch.html)

---

**Última actualización**: 2025-01-09

