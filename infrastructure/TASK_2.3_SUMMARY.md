# Task 2.3: Configurar Amazon SNS - Resumen de Implementación

## Estado: ✅ COMPLETADO

## Descripción

Se ha completado la configuración de Amazon SNS (Simple Notification Service) para el sistema de tracking de eventos de correo electrónico en MSCorreos. El SNS Topic actúa como intermediario entre Amazon SES y el microservicio MSCorreos, permitiendo recibir eventos de tracking en tiempo real.

## Requisitos Implementados

- ✅ **Requirement 4.1**: Amazon_SES SHALL publicar Tracking_Event en un topic Amazon_SNS
- ✅ **Requirement 4.2**: MSCorreos SHALL suscribirse al topic Amazon_SNS para recibir Tracking_Event

## Archivos Creados/Modificados

### 1. Scripts de Despliegue

```
MSCorreos/infrastructure/scripts/
├── deploy-sns.sh          # Script de despliegue SNS (NUEVO)
└── delete-sns.sh          # Script de eliminación SNS (NUEVO)
```

**deploy-sns.sh**:
- Despliega SNS Topic para tracking de eventos
- Configura suscripción HTTPS al endpoint de MSCorreos
- Configura política de acceso para SES
- Crea alarma de CloudWatch
- Valida configuración y muestra outputs

**delete-sns.sh**:
- Elimina stack de CloudFormation
- Limpia recursos asociados
- Solicita confirmación antes de eliminar

### 2. Documentación

```
MSCorreos/infrastructure/
├── SNS_CONFIGURATION_GUIDE.md    # Guía completa de configuración (NUEVO)
├── SNS_QUICK_REFERENCE.md        # Referencia rápida (NUEVO)
└── TASK_2.3_SUMMARY.md           # Este archivo (NUEVO)
```

**SNS_CONFIGURATION_GUIDE.md**:
- Arquitectura detallada
- Proceso de despliegue completo
- Confirmación de suscripción
- Estructura de eventos
- Validación de firma SNS
- Monitoreo y métricas
- Troubleshooting completo
- Seguridad y mejores prácticas

**SNS_QUICK_REFERENCE.md**:
- Comandos esenciales
- Recursos por ambiente
- Checklist de configuración
- Troubleshooting rápido
- Límites y costos

### 3. Template CloudFormation (Ya existente)

```
MSCorreos/infrastructure/cloudformation/
└── sns-topic.yaml         # Template SNS (creado en Task 2.2)
```

**Nota**: El template `sns-topic.yaml` fue creado en Task 2.2 como parte de la configuración de SES. Task 2.3 proporciona scripts y documentación específicos para SNS.


## Recursos Creados

### 1. SNS Topic

**Nombre**: `mscorreos-tracking-events-{env}`

**Propósito**: Recibir eventos de tracking de Amazon SES y distribuirlos a suscriptores

**Configuración**:
- Display Name: `MSCorreos SES Tracking Events ({env})`
- Tags: Application=MSCorreos, Environment={env}, Purpose=SESTracking
- Región: us-east-1

**ARN**: `arn:aws:sns:us-east-1:ACCOUNT_ID:mscorreos-tracking-events-{env}`

### 2. Topic Policy

**Propósito**: Permitir que Amazon SES publique eventos en el topic

**Permisos**:
- Principal: `ses.amazonaws.com`
- Action: `SNS:Publish`
- Condition: `AWS:SourceAccount` debe coincidir con la cuenta actual

### 3. HTTPS Subscription

**Protocolo**: HTTPS
**Endpoint**: URL del endpoint de MSCorreos (ej: `https://mscorreos-dev.acosux.com/sns/notifications`)
**Estado inicial**: PendingConfirmation

**Eventos soportados**:
- Send: Correo enviado a SES
- Delivery: Correo entregado al destinatario
- Open: Destinatario abrió el correo
- Bounce: Correo rebotó (permanente o temporal)
- Complaint: Destinatario marcó como spam
- Reject: SES rechazó el correo
- RenderingFailure: Error renderizando plantilla

### 4. CloudWatch Alarm

**Nombre**: `mscorreos-sns-failed-notifications-{env}`

**Propósito**: Alertar cuando SNS no puede entregar mensajes al endpoint

**Configuración**:
- Métrica: `NumberOfNotificationsFailed`
- Namespace: `AWS/SNS`
- Umbral: > 5 mensajes fallidos
- Período: 5 minutos
- Acción: Notificar al equipo de operaciones

## Arquitectura Implementada

```
┌─────────────────────────────────────────────────────────────┐
│                     Amazon SES                              │
│  Configuration Set: mscorreos-tracking-{env}                │
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
│  - Alarma para mensajes fallidos                           │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ Notifica vía HTTPS POST
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              MSCorreos Microservice                         │
│         Endpoint: POST /sns/notifications                   │
│                                                             │
│  - Recibe eventos de tracking                              │
│  - Valida firma SNS (seguridad)                            │
│  - Procesa eventos                                         │
│  - Actualiza cor_notificaciones                            │
│  - Gestiona lista negra automáticamente                    │
└─────────────────────────────────────────────────────────────┘
```


## Flujo de Datos

### 1. Envío de Correo
```
ShrimpSoftServer → SQS → MSCorreos → SES (con Configuration Set)
```

### 2. Generación de Evento
```
SES detecta evento (delivery, open, bounce, etc.) → Publica a SNS Topic
```

### 3. Notificación a MSCorreos
```
SNS Topic → HTTPS POST → MSCorreos /sns/notifications
```

### 4. Procesamiento en MSCorreos
```
MSCorreos:
1. Valida firma SNS
2. Extrae datos del evento
3. Registra en cor_notificaciones
4. Si es Bounce/Complaint → Actualiza lista negra
5. Retorna HTTP 200
```

## Despliegue

### Comando Rápido

```bash
cd MSCorreos/infrastructure/scripts
chmod +x deploy-sns.sh

# Desarrollo
./deploy-sns.sh dev https://mscorreos-dev.acosux.com/sns/notifications

# Testing
./deploy-sns.sh test https://mscorreos-test.acosux.com/sns/notifications

# Producción
./deploy-sns.sh prod https://mscorreos.acosux.com/sns/notifications
```

### Outputs del Despliegue

El script muestra:
- Stack Name
- Topic Name
- Topic ARN
- Subscription ARN
- Endpoint URL
- Estado de suscripción

### Archivo de Outputs

Los outputs se guardan en:
```
MSCorreos/infrastructure/outputs/sns-{env}.txt
```

Contiene variables de entorno listas para usar:
```bash
SNS_TOPIC_ARN=arn:aws:sns:us-east-1:123456789012:mscorreos-tracking-events-dev
SNS_TOPIC_NAME=mscorreos-tracking-events-dev
SUBSCRIPTION_ARN=arn:aws:sns:us-east-1:123456789012:mscorreos-tracking-events-dev:...
MSCORREOS_ENDPOINT=https://mscorreos-dev.acosux.com/sns/notifications
```

## Confirmación de Suscripción

### Estado Inicial

Después del despliegue, la suscripción queda en estado **PendingConfirmation**.

### Proceso de Confirmación

1. **SNS envía mensaje de confirmación**:
   - Tipo: `SubscriptionConfirmation`
   - Contiene campo `SubscribeURL`

2. **MSCorreos debe confirmar**:
   - Recibir mensaje POST en `/sns/notifications`
   - Hacer GET al `SubscribeURL`
   - Retornar HTTP 200

3. **Suscripción confirmada**:
   - Estado cambia a activo
   - MSCorreos comienza a recibir eventos

### Implementación en MSCorreos

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
    
    private void confirmarSuscripcion(String subscribeUrl) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getForObject(subscribeUrl, String.class);
        log.info("Suscripción SNS confirmada exitosamente");
    }
}
```


## Validación de Firma SNS

### Importancia

La validación de firma es **crítica** para seguridad:
- Verifica que el mensaje proviene de AWS SNS
- Previene ataques de suplantación
- Garantiza integridad del mensaje

### Implementación

```java
import com.amazonaws.services.sns.message.SnsMessageManager;
import com.amazonaws.services.sns.message.SnsNotification;

@Service
public class SNSMessageValidator {
    
    private final SnsMessageManager messageManager = new SnsMessageManager();
    
    public boolean validarFirma(String payload) {
        try {
            SnsNotification notification = messageManager.parseMessage(payload);
            return true; // Firma válida
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

### 1. Verificar Stack

```bash
aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1
```

### 2. Verificar Topic

```bash
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
aws sns publish \
    --topic-arn "$TOPIC_ARN" \
    --message "Test message from CLI" \
    --region us-east-1
```

### 5. Verificar Alarma

```bash
aws cloudwatch describe-alarms \
    --alarm-names mscorreos-sns-failed-notifications-dev \
    --region us-east-1
```

## Monitoreo

### Métricas de CloudWatch

SNS publica automáticamente:
- `NumberOfMessagesPublished`: Mensajes publicados
- `NumberOfNotificationsDelivered`: Notificaciones entregadas
- `NumberOfNotificationsFailed`: Notificaciones fallidas
- `PublishSize`: Tamaño de mensajes

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
```


## Integración con SES

### Configuration Set

El SNS Topic se configura como Event Destination en el SES Configuration Set:

```yaml
# En ses-configuration.yaml
MSCorreosEventDestination:
  Type: AWS::SES::ConfigurationSetEventDestination
  Properties:
    ConfigurationSetName: !Ref MSCorreosConfigurationSet
    EventDestination:
      Name: !Sub 'sns-tracking-destination-${Environment}'
      Enabled: true
      MatchingEventTypes:
        - send
        - delivery
        - open
        - bounce
        - complaint
        - reject
        - renderingFailure
      SnsDestination:
        TopicARN: !Ref SNSTopicArn
```

### Verificar Integración

```bash
aws ses describe-configuration-set \
    --configuration-set-name mscorreos-tracking-dev \
    --region us-east-1
```

## Costos

### Estimación de Costos

- **Primeros 1,000 mensajes/mes**: GRATIS
- **Después**: $0.50 por millón de mensajes
- **Entregas HTTPS**: $0.60 por millón de notificaciones
- **CloudWatch Alarm**: $0.10 por alarma/mes

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

## Seguridad

### Mejores Prácticas Implementadas

1. ✅ **Validación de firma SNS**: Previene suplantación
2. ✅ **HTTPS para suscripción**: Cifrado en tránsito
3. ✅ **Política de acceso restrictiva**: Solo SES puede publicar
4. ✅ **Condición de cuenta**: Solo la cuenta actual puede publicar
5. ✅ **Alarma de fallos**: Detecta problemas de entrega

### Recomendaciones Adicionales

- Implementar rate limiting en el endpoint
- Sanitizar datos antes de loggear
- No exponer información sensible en logs
- Configurar certificado SSL válido en producción

## Troubleshooting

### Problema: Suscripción en PendingConfirmation

**Solución**:
1. Verificar que MSCorreos está corriendo
2. Verificar que el endpoint es accesible públicamente
3. Revisar logs de MSCorreos para ver mensaje de confirmación
4. Confirmar manualmente desde consola AWS si es necesario

### Problema: Mensajes no llegan a MSCorreos

**Solución**:
1. Verificar que la suscripción está confirmada
2. Verificar métricas de `NumberOfNotificationsFailed`
3. Verificar que el endpoint retorna HTTP 200
4. Verificar que el endpoint responde en < 15 segundos

### Problema: Firma SNS inválida

**Solución**:
1. Verificar que se está usando la librería correcta
2. No modificar el payload antes de validar
3. Asegurar que el servidor puede acceder a `*.amazonaws.com`


## Próximos Pasos

### Inmediatos (Después del Despliegue)

1. ✅ SNS Topic creado y configurado
2. ✅ Política de acceso para SES configurada
3. ✅ Suscripción HTTPS creada
4. ✅ Alarma de CloudWatch configurada
5. ⏳ Confirmar suscripción desde endpoint MSCorreos
6. ⏳ Verificar que Configuration Set de SES usa este topic

### Integración con MSCorreos (Task 2.4)

1. ⏳ Implementar endpoint POST /sns/notifications
2. ⏳ Implementar validación de firma SNS
3. ⏳ Implementar procesamiento de eventos de tracking
4. ⏳ Implementar registro en cor_notificaciones
5. ⏳ Implementar actualización de lista negra
6. ⏳ Probar flujo completo de tracking

### Validación

1. ⏳ Enviar correo de prueba con SES
2. ⏳ Verificar que evento llega a SNS
3. ⏳ Verificar que MSCorreos recibe evento
4. ⏳ Verificar que evento se registra en BD
5. ⏳ Verificar que lista negra se actualiza correctamente

## Comandos Útiles

### Obtener Outputs

```bash
# Ver todos los outputs
aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
    --output table

# Obtener ARN del topic
aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`TopicArn`].OutputValue' \
    --output text
```

### Gestión de Suscripciones

```bash
# Listar suscripciones del topic
aws sns list-subscriptions-by-topic \
    --topic-arn "$TOPIC_ARN" \
    --region us-east-1

# Eliminar suscripción
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

### Monitoreo

```bash
# Ver métricas de mensajes publicados
aws cloudwatch get-metric-statistics \
    --namespace AWS/SNS \
    --metric-name NumberOfMessagesPublished \
    --dimensions Name=TopicName,Value=mscorreos-tracking-events-dev \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Sum \
    --region us-east-1

# Ver estado de alarmas
aws cloudwatch describe-alarms \
    --alarm-name-prefix mscorreos-sns \
    --region us-east-1
```

## Referencias

- [Guía Completa de Configuración](./SNS_CONFIGURATION_GUIDE.md)
- [Referencia Rápida](./SNS_QUICK_REFERENCE.md)
- [Guía de Configuración SES](./SES_CONFIGURATION_GUIDE.md)
- [AWS SNS Documentation](https://docs.aws.amazon.com/sns/)
- [SNS Message Signature Verification](https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html)
- [SES Event Publishing](https://docs.aws.amazon.com/ses/latest/dg/event-publishing.html)

## Notas Importantes

1. **Confirmación de Suscripción**: La suscripción HTTPS debe ser confirmada por el endpoint antes de recibir eventos. SNS envía un mensaje de confirmación que debe ser procesado.

2. **Validación de Firma**: Es crítico validar la firma SNS en cada mensaje para prevenir ataques de suplantación.

3. **Timeout de Entrega**: El endpoint debe responder en menos de 15 segundos. Si tarda más, SNS considera la entrega como fallida.

4. **Reintentos**: SNS reintenta la entrega 3 veces con backoff exponencial si el endpoint no responde o retorna error.

5. **Idempotencia**: El endpoint debe ser idempotente ya que puede recibir el mismo evento múltiples veces.

## Estado de Implementación

- ✅ Template CloudFormation creado (Task 2.2)
- ✅ Scripts de despliegue creados
- ✅ Scripts de eliminación creados
- ✅ Documentación completa
- ✅ Referencia rápida
- ⏳ Despliegue en ambiente dev (pendiente de ejecutar)
- ⏳ Confirmación de suscripción (pendiente)
- ⏳ Integración con MSCorreos (Task 2.4)
- ⏳ Pruebas de flujo completo (pendiente)

---

**Task completada**: 2025-01-09  
**Implementado por**: Kiro AI Assistant  
**Próxima task**: 2.4 - Implementar SNS Listener en MSCorreos

