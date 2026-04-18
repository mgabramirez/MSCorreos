# Guía de Configuración de Amazon SNS para MSCorreos

## Descripción General

Esta guía describe la configuración completa de Amazon SNS (Simple Notification Service) para el sistema de tracking de eventos de correo electrónico en MSCorreos. El SNS Topic actúa como intermediario entre Amazon SES y el microservicio MSCorreos, permitiendo recibir eventos de tracking en tiempo real.

## Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                     Amazon SES                              │
│  - Envía correos electrónicos                               │
│  - Genera eventos de tracking                               │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ Publica eventos
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Amazon SNS Topic                               │
│         mscorreos-tracking-events-{env}                     │
│                                                             │
│  - Recibe eventos de SES                                   │
│  - Política de acceso para SES                             │
│  - Distribuye a suscriptores                               │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ Notifica vía HTTPS
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              MSCorreos Microservice                         │
│         Endpoint: POST /sns/notifications                   │
│                                                             │
│  - Recibe eventos de tracking                              │
│  - Valida firma SNS                                        │
│  - Procesa eventos                                         │
│  - Actualiza base de datos                                 │
│  - Gestiona lista negra                                    │
└─────────────────────────────────────────────────────────────┘
```

## Requisitos Previos

### 1. Herramientas Necesarias

- **AWS CLI** (versión 2.x o superior)
- **jq** (para procesamiento JSON)
- **Permisos IAM** necesarios

### 2. Permisos IAM Requeridos

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sns:*",
        "cloudformation:*",
        "cloudwatch:*"
      ],
      "Resource": "*"
    }
  ]
}
```


### 3. Endpoint de MSCorreos

Debe tener un endpoint HTTPS accesible públicamente:
- **Desarrollo**: `https://mscorreos-dev.acosux.com/sns/notifications`
- **Testing**: `https://mscorreos-test.acosux.com/sns/notifications`
- **Producción**: `https://mscorreos.acosux.com/sns/notifications`

## Despliegue Rápido

### Opción 1: Script Automatizado (Recomendado)

```bash
cd MSCorreos/infrastructure/scripts

# Dar permisos de ejecución
chmod +x deploy-sns.sh

# Desplegar para desarrollo
./deploy-sns.sh dev https://mscorreos-dev.acosux.com/sns/notifications

# Desplegar para testing
./deploy-sns.sh test https://mscorreos-test.acosux.com/sns/notifications

# Desplegar para producción
./deploy-sns.sh prod https://mscorreos.acosux.com/sns/notifications
```

### Opción 2: Despliegue Manual con AWS CLI

```bash
cd MSCorreos/infrastructure/cloudformation

# Validar template
aws cloudformation validate-template \
    --template-body file://sns-topic.yaml \
    --region us-east-1

# Crear stack
aws cloudformation create-stack \
    --stack-name mscorreos-sns-tracking-dev \
    --template-body file://sns-topic.yaml \
    --parameters \
        ParameterKey=Environment,ParameterValue=dev \
        ParameterKey=MSCorreosEndpoint,ParameterValue=https://mscorreos-dev.acosux.com/sns/notifications \
    --region us-east-1 \
    --capabilities CAPABILITY_IAM

# Esperar a que se complete
aws cloudformation wait stack-create-complete \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1
```


## Recursos Creados

### 1. SNS Topic

**Nombre**: `mscorreos-tracking-events-{env}`

**Propósito**: Recibir eventos de tracking de Amazon SES

**Configuración**:
- Display Name: `MSCorreos SES Tracking Events ({env})`
- Tags: Application=MSCorreos, Environment={env}, Purpose=SESTracking

### 2. Topic Policy

**Propósito**: Permitir que Amazon SES publique eventos en el topic

**Política**:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowSESToPublish",
      "Effect": "Allow",
      "Principal": {
        "Service": "ses.amazonaws.com"
      },
      "Action": "SNS:Publish",
      "Resource": "arn:aws:sns:us-east-1:ACCOUNT_ID:mscorreos-tracking-events-{env}",
      "Condition": {
        "StringEquals": {
          "AWS:SourceAccount": "ACCOUNT_ID"
        }
      }
    }
  ]
}
```

### 3. HTTPS Subscription

**Protocolo**: HTTPS
**Endpoint**: URL del endpoint de MSCorreos
**Estado inicial**: PendingConfirmation

**Nota**: La suscripción debe ser confirmada por el endpoint antes de recibir eventos.

### 4. CloudWatch Alarm

**Nombre**: `mscorreos-sns-failed-notifications-{env}`

**Propósito**: Alertar cuando SNS no puede entregar mensajes

**Configuración**:
- Métrica: `NumberOfNotificationsFailed`
- Umbral: > 5 mensajes fallidos
- Período: 5 minutos
- Acción: Notificar al equipo de operaciones


## Confirmación de Suscripción

### Proceso de Confirmación

1. **SNS envía mensaje de confirmación**:
   - Después del despliegue, SNS envía un mensaje POST al endpoint
   - El mensaje contiene el campo `SubscribeURL`

2. **Estructura del mensaje de confirmación**:
```json
{
  "Type": "SubscriptionConfirmation",
  "MessageId": "165545c9-2a5c-472c-8df2-7ff2be2b3b1b",
  "Token": "2336412f37...",
  "TopicArn": "arn:aws:sns:us-east-1:123456789012:mscorreos-tracking-events-dev",
  "Message": "You have chosen to subscribe to the topic...",
  "SubscribeURL": "https://sns.us-east-1.amazonaws.com/?Action=ConfirmSubscription&TopicArn=...",
  "Timestamp": "2025-01-09T12:00:00.000Z",
  "SignatureVersion": "1",
  "Signature": "EXAMPLEpH+...",
  "SigningCertURL": "https://sns.us-east-1.amazonaws.com/..."
}
```

3. **MSCorreos debe confirmar**:
   - Hacer una petición GET al `SubscribeURL`
   - O confirmar manualmente desde la consola AWS

### Implementación en MSCorreos

```java
@RestController
@RequestMapping("/sns")
public class SNSListener {
    
    @PostMapping("/notifications")
    public ResponseEntity<Void> receiveNotification(@RequestBody String payload) {
        // Parsear mensaje SNS
        Map<String, Object> snsMessage = parseJson(payload);
        String type = (String) snsMessage.get("Type");
        
        if ("SubscriptionConfirmation".equals(type)) {
            // Confirmar suscripción
            String subscribeUrl = (String) snsMessage.get("SubscribeURL");
            confirmarSuscripcion(subscribeUrl);
            return ResponseEntity.ok().build();
        }
        
        // Procesar otros tipos de mensajes...
        return ResponseEntity.ok().build();
    }
    
    private void confirmarSuscripcion(String subscribeUrl) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getForObject(subscribeUrl, String.class);
        log.info("Suscripción SNS confirmada exitosamente");
    }
}
```

### Confirmación Manual

Si el endpoint no está disponible, puede confirmar manualmente:

```bash
# Obtener ARN de la suscripción
SUBSCRIPTION_ARN=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`SubscriptionArn`].OutputValue' \
    --output text)

# Verificar estado
aws sns get-subscription-attributes \
    --subscription-arn "$SUBSCRIPTION_ARN" \
    --region us-east-1

# Confirmar desde consola AWS:
# https://console.aws.amazon.com/sns/v3/home?region=us-east-1#/subscriptions
```


## Eventos de Tracking Soportados

### Tipos de Eventos

1. **Send**: Correo enviado exitosamente a SES
2. **Delivery**: Correo entregado al servidor del destinatario
3. **Open**: Destinatario abrió el correo
4. **Bounce**: Correo rebotó (permanente o temporal)
5. **Complaint**: Destinatario marcó como spam
6. **Reject**: SES rechazó el correo
7. **RenderingFailure**: Error renderizando plantilla

### Estructura de Evento de Tracking

```json
{
  "Type": "Notification",
  "MessageId": "22b80b92-fdea-4c2c-8f9d-bdfb0c7bf324",
  "TopicArn": "arn:aws:sns:us-east-1:123456789012:mscorreos-tracking-events-dev",
  "Message": "{\"eventType\":\"Delivery\",\"mail\":{...},\"delivery\":{...}}",
  "Timestamp": "2025-01-09T12:00:00.000Z",
  "SignatureVersion": "1",
  "Signature": "EXAMPLE2AfsSqr...",
  "SigningCertURL": "https://sns.us-east-1.amazonaws.com/...",
  "UnsubscribeURL": "https://sns.us-east-1.amazonaws.com/..."
}
```

### Contenido del Campo "Message"

#### Evento: Delivery
```json
{
  "eventType": "Delivery",
  "mail": {
    "timestamp": "2025-01-09T12:00:00.000Z",
    "source": "notificaciones@documentos-electronicos.info",
    "messageId": "0000014a8a8c1234-abcd1234-1234-1234-1234-abcd12345678-000000",
    "destination": ["cliente@example.com"],
    "tags": {
      "ows-empresa": ["ACOSUX"],
      "ows-ruc": ["1234567890001"],
      "ows-tipo-notificacion": ["NOTIFICAR_VENTA_ELECTRONICA_EMITIDA"]
    }
  },
  "delivery": {
    "timestamp": "2025-01-09T12:00:05.000Z",
    "processingTimeMillis": 5000,
    "recipients": ["cliente@example.com"],
    "smtpResponse": "250 2.0.0 OK 1234567890 abcd1234",
    "reportingMTA": "a8-123.smtp-out.amazonses.com"
  }
}
```

#### Evento: Bounce
```json
{
  "eventType": "Bounce",
  "bounce": {
    "bounceType": "Permanent",
    "bounceSubType": "General",
    "bouncedRecipients": [
      {
        "emailAddress": "invalido@example.com",
        "action": "failed",
        "status": "5.1.1",
        "diagnosticCode": "smtp; 550 5.1.1 user unknown"
      }
    ],
    "timestamp": "2025-01-09T12:00:05.000Z",
    "feedbackId": "0000014a8a8c1234-abcd1234-1234-1234-1234-abcd12345678-000000"
  }
}
```


## Validación de Firma SNS

### Importancia

La validación de firma es **crítica** para seguridad:
- Verifica que el mensaje proviene realmente de AWS SNS
- Previene ataques de suplantación
- Garantiza integridad del mensaje

### Implementación en Java

```java
import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.message.SnsNotification;

@Service
public class SNSMessageValidator {
    
    private final SnsMessageManager messageManager = new SnsMessageManager();
    
    public boolean validarFirma(String payload) {
        try {
            // Parsear y validar mensaje
            SnsNotification notification = messageManager.parseMessage(payload);
            
            // La validación de firma se hace automáticamente
            // Si llega aquí, la firma es válida
            return true;
        } catch (Exception e) {
            log.error("Firma SNS inválida", e);
            return false;
        }
    }
}
```

### Dependencia Maven

```xml
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-java-sdk-sns</artifactId>
    <version>1.12.529</version>
</dependency>
```

## Verificación del Despliegue

### 1. Verificar Stack de CloudFormation

```bash
# Ver estado del stack
aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1

# Ver outputs
aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
    --output table
```

### 2. Verificar Topic SNS

```bash
# Listar topics
aws sns list-topics --region us-east-1 | grep mscorreos

# Obtener atributos del topic
TOPIC_ARN=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`TopicArn`].OutputValue' \
    --output text)

aws sns get-topic-attributes \
    --topic-arn "$TOPIC_ARN" \
    --region us-east-1
```

### 3. Verificar Suscripción

```bash
# Listar suscripciones del topic
aws sns list-subscriptions-by-topic \
    --topic-arn "$TOPIC_ARN" \
    --region us-east-1

# Ver estado de suscripción específica
SUBSCRIPTION_ARN=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`SubscriptionArn`].OutputValue' \
    --output text)

aws sns get-subscription-attributes \
    --subscription-arn "$SUBSCRIPTION_ARN" \
    --region us-east-1
```


### 4. Publicar Mensaje de Prueba

```bash
# Publicar mensaje simple
aws sns publish \
    --topic-arn "$TOPIC_ARN" \
    --message "Test message from CLI" \
    --region us-east-1

# Publicar mensaje con estructura de evento
aws sns publish \
    --topic-arn "$TOPIC_ARN" \
    --message file://test-event.json \
    --region us-east-1
```

**test-event.json**:
```json
{
  "eventType": "Delivery",
  "mail": {
    "timestamp": "2025-01-09T12:00:00.000Z",
    "messageId": "test-message-id",
    "destination": ["test@example.com"],
    "tags": {
      "ows-empresa": ["TEST"],
      "ows-tipo-notificacion": ["TEST"]
    }
  },
  "delivery": {
    "timestamp": "2025-01-09T12:00:05.000Z",
    "recipients": ["test@example.com"]
  }
}
```

### 5. Verificar Alarmas

```bash
# Listar alarmas relacionadas con SNS
aws cloudwatch describe-alarms \
    --alarm-name-prefix mscorreos-sns \
    --region us-east-1

# Ver estado de alarma específica
aws cloudwatch describe-alarms \
    --alarm-names mscorreos-sns-failed-notifications-dev \
    --region us-east-1
```

## Integración con SES

### Configurar Event Destination en SES

El SNS Topic debe configurarse como Event Destination en el SES Configuration Set:

```bash
# Esto se hace automáticamente en el template ses-configuration.yaml
# Pero puede verificarse con:

aws ses describe-configuration-set \
    --configuration-set-name mscorreos-tracking-dev \
    --region us-east-1
```

### Verificar Flujo Completo

1. **Enviar correo de prueba con SES**:
```bash
aws ses send-email \
    --from notificaciones@documentos-electronicos.info \
    --destination ToAddresses=test@example.com \
    --message Subject={Data="Test"},Body={Text={Data="Test message"}} \
    --configuration-set-name mscorreos-tracking-dev \
    --region us-east-1
```

2. **Verificar que SNS recibe el evento**:
   - Revisar logs de MSCorreos
   - Verificar métricas de CloudWatch
   - Consultar tabla cor_notificaciones


## Monitoreo y Métricas

### Métricas de CloudWatch

SNS publica automáticamente las siguientes métricas:

1. **NumberOfMessagesPublished**: Mensajes publicados al topic
2. **NumberOfNotificationsDelivered**: Notificaciones entregadas exitosamente
3. **NumberOfNotificationsFailed**: Notificaciones fallidas
4. **PublishSize**: Tamaño de los mensajes publicados

### Ver Métricas

```bash
# Mensajes publicados en la última hora
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

### Dashboard de CloudWatch

Crear un dashboard personalizado:

```bash
aws cloudwatch put-dashboard \
    --dashboard-name MSCorreos-SNS-Monitoring \
    --dashboard-body file://sns-dashboard.json \
    --region us-east-1
```

**sns-dashboard.json**:
```json
{
  "widgets": [
    {
      "type": "metric",
      "properties": {
        "metrics": [
          ["AWS/SNS", "NumberOfMessagesPublished", {"stat": "Sum"}],
          [".", "NumberOfNotificationsDelivered", {"stat": "Sum"}],
          [".", "NumberOfNotificationsFailed", {"stat": "Sum"}]
        ],
        "period": 300,
        "stat": "Sum",
        "region": "us-east-1",
        "title": "SNS Topic Metrics"
      }
    }
  ]
}
```


## Troubleshooting

### Problema: Suscripción en estado "PendingConfirmation"

**Síntomas**:
- La suscripción no se confirma automáticamente
- No se reciben eventos en MSCorreos

**Soluciones**:

1. **Verificar que el endpoint está accesible**:
```bash
curl -X POST https://mscorreos-dev.acosux.com/sns/notifications \
    -H "Content-Type: application/json" \
    -d '{"test": "message"}'
```

2. **Revisar logs de MSCorreos**:
   - Buscar el mensaje de confirmación de SNS
   - Verificar que se está llamando al SubscribeURL

3. **Confirmar manualmente desde consola AWS**:
   - Ir a: https://console.aws.amazon.com/sns/v3/home?region=us-east-1#/subscriptions
   - Buscar la suscripción pendiente
   - Hacer clic en "Confirm subscription"

4. **Reenviar mensaje de confirmación**:
```bash
# Eliminar suscripción existente
aws sns unsubscribe \
    --subscription-arn "$SUBSCRIPTION_ARN" \
    --region us-east-1

# Crear nueva suscripción
aws sns subscribe \
    --topic-arn "$TOPIC_ARN" \
    --protocol https \
    --notification-endpoint https://mscorreos-dev.acosux.com/sns/notifications \
    --region us-east-1
```

### Problema: Mensajes no llegan a MSCorreos

**Síntomas**:
- SES envía correos correctamente
- SNS no entrega mensajes al endpoint

**Soluciones**:

1. **Verificar que la suscripción está confirmada**:
```bash
aws sns get-subscription-attributes \
    --subscription-arn "$SUBSCRIPTION_ARN" \
    --region us-east-1 \
    --query 'Attributes.PendingConfirmation'
```

2. **Verificar métricas de fallos**:
```bash
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

3. **Verificar que el endpoint responde correctamente**:
   - El endpoint debe retornar HTTP 200
   - El endpoint debe procesar el mensaje en menos de 15 segundos
   - El endpoint debe estar disponible públicamente (no detrás de VPN)

4. **Revisar alarmas de CloudWatch**:
```bash
aws cloudwatch describe-alarm-history \
    --alarm-name mscorreos-sns-failed-notifications-dev \
    --region us-east-1
```


### Problema: Firma SNS inválida

**Síntomas**:
- MSCorreos rechaza mensajes con error de validación de firma
- Logs muestran "Invalid SNS signature"

**Soluciones**:

1. **Verificar que se está usando la librería correcta**:
```xml
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>aws-java-sdk-sns</artifactId>
    <version>1.12.529</version>
</dependency>
```

2. **Verificar que el certificado SNS es válido**:
   - SNS incluye `SigningCertURL` en cada mensaje
   - La librería descarga y valida el certificado automáticamente
   - Asegurar que el servidor puede acceder a `*.amazonaws.com`

3. **No modificar el payload antes de validar**:
   - La firma se calcula sobre el payload completo
   - Cualquier modificación invalida la firma

### Problema: Eventos duplicados

**Síntomas**:
- MSCorreos recibe el mismo evento múltiples veces
- Registros duplicados en cor_notificaciones

**Soluciones**:

1. **Implementar idempotencia**:
```java
@Service
public class TrackingEventService {
    
    private final Set<String> processedMessageIds = 
        ConcurrentHashMap.newKeySet();
    
    public void procesarEvento(TrackingEvent evento) {
        String messageId = evento.getMail().getMessageId();
        
        // Verificar si ya fue procesado
        if (processedMessageIds.contains(messageId)) {
            log.warn("Evento duplicado ignorado: {}", messageId);
            return;
        }
        
        // Procesar evento
        // ...
        
        // Marcar como procesado
        processedMessageIds.add(messageId);
    }
}
```

2. **Usar constraint UNIQUE en base de datos**:
```sql
ALTER TABLE correos.cor_notificaciones 
ADD CONSTRAINT uk_message_id_tipo 
UNIQUE (n_informe->>'messageId', n_tipo);
```

### Problema: Alta latencia en entrega

**Síntomas**:
- Eventos tardan varios segundos en llegar a MSCorreos
- Delay entre envío de correo y registro en BD

**Soluciones**:

1. **Verificar performance del endpoint**:
   - El endpoint debe responder en < 1 segundo
   - Procesar eventos de forma asíncrona si es necesario

2. **Optimizar procesamiento**:
```java
@Service
public class TrackingEventService {
    
    @Async
    public CompletableFuture<Void> procesarEventoAsync(TrackingEvent evento) {
        // Procesamiento asíncrono
        return CompletableFuture.completedFuture(null);
    }
}
```

3. **Verificar recursos del servidor**:
   - CPU, memoria, red
   - Escalar horizontalmente si es necesario


## Seguridad

### Mejores Prácticas

1. **Siempre validar firma SNS**:
   - No confiar en mensajes sin validación
   - Usar librería oficial de AWS

2. **Usar HTTPS para suscripción**:
   - Nunca usar HTTP en producción
   - Configurar certificado SSL válido

3. **Restringir acceso al endpoint**:
   - Validar que los mensajes provienen de SNS
   - Implementar rate limiting

4. **No exponer información sensible en logs**:
   - Sanitizar datos antes de loggear
   - No loggear contenido completo de correos

### Política de Acceso Restrictiva

Si necesita restringir aún más el acceso:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowSESToPublish",
      "Effect": "Allow",
      "Principal": {
        "Service": "ses.amazonaws.com"
      },
      "Action": "SNS:Publish",
      "Resource": "arn:aws:sns:us-east-1:ACCOUNT_ID:mscorreos-tracking-events-*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceAccount": "ACCOUNT_ID"
        },
        "StringLike": {
          "AWS:SourceArn": "arn:aws:ses:us-east-1:ACCOUNT_ID:configuration-set/mscorreos-tracking-*"
        }
      }
    }
  ]
}
```

## Costos

### Estimación de Costos

SNS tiene costos muy bajos:

- **Primeros 1,000 mensajes/mes**: GRATIS
- **Después**: $0.50 por millón de mensajes
- **Entregas HTTPS**: $0.60 por millón de notificaciones
- **CloudWatch Alarms**: $0.10 por alarma/mes

### Ejemplo de Cálculo

Para un sistema que envía **10,000 correos/día**:

- Correos/mes: 10,000 × 30 = 300,000
- Eventos por correo: ~3 (Send, Delivery, Open)
- Total eventos/mes: 300,000 × 3 = 900,000

**Costo mensual**:
- Mensajes SNS: $0.50 × 0.9 = $0.45
- Entregas HTTPS: $0.60 × 0.9 = $0.54
- Alarma CloudWatch: $0.10
- **Total**: ~$1.09 USD/mes


## Eliminación de Recursos

### Script Automatizado

```bash
cd MSCorreos/infrastructure/scripts
chmod +x delete-sns.sh
./delete-sns.sh dev
```

### Eliminación Manual

```bash
# Eliminar stack de CloudFormation
aws cloudformation delete-stack \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1

# Esperar a que se complete
aws cloudformation wait stack-delete-complete \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1
```

**⚠️ ADVERTENCIA**: Eliminar el SNS Topic detendrá el tracking de eventos de SES.

## Referencias

- [AWS SNS Documentation](https://docs.aws.amazon.com/sns/)
- [SNS Message Signature Verification](https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html)
- [SES Event Publishing](https://docs.aws.amazon.com/ses/latest/dg/event-publishing.html)
- [CloudWatch Metrics for SNS](https://docs.aws.amazon.com/sns/latest/dg/sns-monitoring-using-cloudwatch.html)

## Próximos Pasos

Después de configurar SNS:

1. ✅ SNS Topic creado y configurado
2. ⏳ Confirmar suscripción desde MSCorreos
3. ⏳ Implementar SNS Listener en MSCorreos (Task 2.4)
4. ⏳ Probar flujo completo de tracking
5. ⏳ Configurar alertas adicionales según necesidades

## Soporte

Para problemas o preguntas:
- Revisar logs de CloudFormation
- Consultar métricas de CloudWatch
- Verificar estado de suscripción SNS
- Contactar al equipo de DevOps/Infraestructura

---

**Documento creado**: 2025-01-09  
**Última actualización**: 2025-01-09  
**Versión**: 1.0  
**Autor**: Kiro AI Assistant

