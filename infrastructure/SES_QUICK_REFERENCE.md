# Amazon SES - Referencia Rápida

## Comandos Esenciales

### Despliegue

```bash
# Desplegar SES completo (recomendado)
cd MSCorreos/infrastructure/scripts
./deploy-ses.sh dev https://mscorreos-dev.acosux.com/sns/notifications

# Eliminar configuración SES
./delete-ses.sh dev
```

### Verificación de Estado

```bash
# Ver stacks de CloudFormation
aws cloudformation list-stacks --region us-east-1 | grep mscorreos

# Ver Configuration Set
aws ses describe-configuration-set \
    --configuration-set-name mscorreos-tracking-dev \
    --region us-east-1

# Ver identidades verificadas
aws ses list-identities --region us-east-1

# Ver límites de envío
aws ses get-send-quota --region us-east-1

# Verificar si está en sandbox
aws sesv2 get-account --region us-east-1 --query 'ProductionAccess'
```

### Verificación de Dominio

```bash
# Iniciar verificación
aws ses verify-domain-identity \
    --domain documentos-electronicos.info \
    --region us-east-1

# Obtener token DNS
aws ses verify-domain-identity \
    --domain documentos-electronicos.info \
    --region us-east-1 \
    --query 'VerificationToken' \
    --output text

# Verificar estado
aws ses get-identity-verification-attributes \
    --identities documentos-electronicos.info \
    --region us-east-1
```

### Verificación de Email

```bash
# Verificar email
aws ses verify-email-identity \
    --email-address notificaciones@documentos-electronicos.info \
    --region us-east-1

# Listar emails verificados
aws ses list-verified-email-addresses --region us-east-1
```

### SNS Topic

```bash
# Listar topics
aws sns list-topics --region us-east-1 | grep mscorreos

# Ver suscripciones
SNS_TOPIC_ARN="arn:aws:sns:us-east-1:ACCOUNT_ID:mscorreos-tracking-events-dev"
aws sns list-subscriptions-by-topic \
    --topic-arn $SNS_TOPIC_ARN \
    --region us-east-1

# Confirmar suscripción manualmente (si es necesario)
aws sns confirm-subscription \
    --topic-arn $SNS_TOPIC_ARN \
    --token "TOKEN_FROM_CONFIRMATION_MESSAGE" \
    --region us-east-1
```

### Pruebas de Envío

```bash
# Enviar correo de prueba (solo si está fuera de sandbox)
aws ses send-email \
    --from notificaciones@documentos-electronicos.info \
    --destination ToAddresses=test@example.com \
    --message Subject={Data="Test",Charset=utf-8},Body={Text={Data="Test",Charset=utf-8}} \
    --configuration-set-name mscorreos-tracking-dev \
    --region us-east-1

# Enviar correo con tags
aws ses send-email \
    --from notificaciones@documentos-electronicos.info \
    --destination ToAddresses=test@example.com \
    --message Subject={Data="Test",Charset=utf-8},Body={Text={Data="Test",Charset=utf-8}} \
    --configuration-set-name mscorreos-tracking-dev \
    --tags Name=ows-empresa,Value=TEST Name=ows-tipo-notificacion,Value=TEST \
    --region us-east-1
```

### Monitoreo

```bash
# Ver estadísticas de envío
aws ses get-send-statistics --region us-east-1

# Ver métricas de bounce rate
aws cloudwatch get-metric-statistics \
    --namespace AWS/SES \
    --metric-name Reputation.BounceRate \
    --dimensions Name=ConfigurationSet,Value=mscorreos-tracking-dev \
    --start-time $(date -u -d '7 days ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 86400 \
    --statistics Average \
    --region us-east-1

# Ver alarmas
aws cloudwatch describe-alarms \
    --alarm-name-prefix mscorreos-ses \
    --region us-east-1
```

### Troubleshooting

```bash
# Ver logs de CloudFormation
aws cloudformation describe-stack-events \
    --stack-name mscorreos-ses-config-dev \
    --region us-east-1 \
    --max-items 20

# Ver eventos de SES
aws ses get-send-statistics --region us-east-1

# Verificar política del topic SNS
aws sns get-topic-attributes \
    --topic-arn $SNS_TOPIC_ARN \
    --region us-east-1 \
    --query 'Attributes.Policy'
```

## Checklist de Configuración

### Despliegue Inicial

- [ ] Ejecutar `./deploy-ses.sh dev`
- [ ] Agregar registro TXT a DNS para verificar dominio
- [ ] Confirmar email de verificación desde buzón
- [ ] Confirmar suscripción SNS desde endpoint MSCorreos
- [ ] Verificar que Configuration Set está creado
- [ ] Verificar que Event Destination apunta a SNS
- [ ] Verificar que alarmas de CloudWatch están activas

### Salir de Sandbox (Producción)

- [ ] Solicitar acceso de producción desde consola SES
- [ ] Esperar aprobación de AWS (24-48 horas)
- [ ] Verificar que `ProductionAccess = true`
- [ ] Probar envío a correos no verificados
- [ ] Monitorear bounce rate y complaint rate

### Configuración de Límites

- [ ] Verificar límites actuales con `get-send-quota`
- [ ] Si necesita más, solicitar aumento a AWS Support
- [ ] Configurar throttling en aplicación (14 correos/segundo)
- [ ] Implementar monitoreo de uso

### Integración con MSCorreos

- [ ] Configurar variables de entorno en MSCorreos
- [ ] Implementar SNS Listener endpoint
- [ ] Implementar SES Adapter para envío
- [ ] Probar envío de correo desde aplicación
- [ ] Verificar que eventos llegan al endpoint SNS
- [ ] Verificar que eventos se registran en BD

## Valores de Configuración

### Desarrollo

```
Environment: dev
Configuration Set: mscorreos-tracking-dev
SNS Topic: mscorreos-tracking-events-dev
Endpoint: https://mscorreos-dev.acosux.com/sns/notifications
```

### Testing

```
Environment: test
Configuration Set: mscorreos-tracking-test
SNS Topic: mscorreos-tracking-events-test
Endpoint: https://mscorreos-test.acosux.com/sns/notifications
```

### Producción

```
Environment: prod
Configuration Set: mscorreos-tracking-prod
SNS Topic: mscorreos-tracking-events-prod
Endpoint: https://mscorreos.acosux.com/sns/notifications
```

## Límites y Cuotas

### Modo Sandbox

- Tasa de envío: 1 correo/segundo
- Cuota diaria: 200 correos/día
- Solo puede enviar a direcciones verificadas

### Modo Producción (Inicial)

- Tasa de envío: 14 correos/segundo
- Cuota diaria: 50,000 correos/día
- Puede enviar a cualquier dirección

### Límites de Mensaje

- Tamaño máximo: 10 MB (incluyendo adjuntos)
- Máximo de destinatarios por mensaje: 50
- Máximo de adjuntos: Sin límite específico (limitado por tamaño total)

## Eventos de Tracking

### Tipos de Eventos

- **send**: Correo enviado exitosamente a SES
- **delivery**: Correo entregado al servidor del destinatario
- **open**: Destinatario abrió el correo (requiere tracking pixel)
- **bounce**: Correo rebotó (permanente o temporal)
- **complaint**: Destinatario marcó como spam
- **reject**: SES rechazó el correo (email inválido, contenido problemático)
- **renderingFailure**: Error renderizando plantilla

### Estructura de Evento SNS

```json
{
  "eventType": "Bounce",
  "mail": {
    "timestamp": "2024-01-15T10:30:00.000Z",
    "messageId": "abc123...",
    "source": "notificaciones@documentos-electronicos.info",
    "destination": ["destinatario@example.com"],
    "tags": {
      "ows-empresa": ["ACOSUX"],
      "ows-tipo-notificacion": ["NOTIFICAR_VENTA_ELECTRONICA_EMITIDA"]
    }
  },
  "bounce": {
    "bounceType": "Permanent",
    "bounceSubType": "General",
    "bouncedRecipients": [
      {
        "emailAddress": "destinatario@example.com",
        "status": "5.1.1",
        "diagnosticCode": "smtp; 550 5.1.1 user unknown"
      }
    ]
  }
}
```

## Mejores Prácticas

### Reputación de Envío

- ✅ Mantener bounce rate < 5%
- ✅ Mantener complaint rate < 0.1%
- ✅ Implementar lista negra automática
- ✅ Validar formato de emails antes de enviar
- ✅ Usar double opt-in para nuevos destinatarios
- ✅ Incluir enlace de unsubscribe en correos

### Seguridad

- ✅ Usar Configuration Set para tracking
- ✅ Validar firma de mensajes SNS
- ✅ Usar HTTPS para endpoint SNS
- ✅ No exponer credenciales en código
- ✅ Usar roles de IAM en lugar de access keys
- ✅ Rotar credenciales regularmente

### Performance

- ✅ Implementar throttling (14 correos/segundo)
- ✅ Usar colas SQS para controlar flujo
- ✅ Implementar reintentos con backoff exponencial
- ✅ Monitorear métricas de CloudWatch
- ✅ Configurar alarmas para anomalías

## Recursos Útiles

- [Documentación SES](https://docs.aws.amazon.com/ses/)
- [Guía Completa](./SES_CONFIGURATION_GUIDE.md)
- [Guía de Despliegue General](./DEPLOYMENT_GUIDE.md)
- [Consola SES](https://console.aws.amazon.com/ses/home)
- [Consola SNS](https://console.aws.amazon.com/sns/home)
- [Consola CloudWatch](https://console.aws.amazon.com/cloudwatch/home)
