# Guía de Configuración de Amazon SES para MSCorreos

Esta guía describe el proceso completo para configurar Amazon SES (Simple Email Service) para el sistema de notificaciones MSCorreos, incluyendo Configuration Set, Event Destination, verificación de identidades y configuración de límites.

## Tabla de Contenidos

1. [Requisitos Previos](#requisitos-previos)
2. [Arquitectura de SES](#arquitectura-de-ses)
3. [Despliegue Automático](#despliegue-automático)
4. [Configuración Manual](#configuración-manual)
5. [Verificación de Identidades](#verificación-de-identidades)
6. [Salir del Modo Sandbox](#salir-del-modo-sandbox)
7. [Configuración de Límites](#configuración-de-límites)
8. [Verificación del Despliegue](#verificación-del-despliegue)
9. [Troubleshooting](#troubleshooting)

## Requisitos Previos

### 1. Herramientas Necesarias

- **AWS CLI** (versión 2.x o superior)
- **jq** (para procesamiento de JSON)
- **bc** (para cálculos en bash)

```bash
# Verificar instalaciones
aws --version
jq --version
bc --version
```

### 2. Credenciales AWS Configuradas

```bash
aws configure
# Proporcionar:
# - AWS Access Key ID
# - AWS Secret Access Key
# - Default region: us-east-1
# - Default output format: json
```

### 3. Permisos IAM Necesarios

El usuario/rol debe tener los siguientes permisos:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ses:*",
        "sns:*",
        "cloudformation:*",
        "cloudwatch:*",
        "iam:CreateRole",
        "iam:AttachRolePolicy"
      ],
      "Resource": "*"
    }
  ]
}
```

### 4. Infraestructura Previa

Antes de configurar SES, debe tener desplegada la infraestructura SQS (Task 2.1):

```bash
# Verificar que existen las colas SQS
aws sqs list-queues --region us-east-1 | grep mscorreos
```

## Arquitectura de SES

### Componentes Principales

```
┌─────────────────────────────────────────────────────────────┐
│                     Amazon SES                              │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Configuration Set: mscorreos-tracking-{env}         │  │
│  │                                                      │  │
│  │  - Tracking de eventos habilitado                   │  │
│  │  - Métricas de reputación habilitadas               │  │
│  │  - Supresión automática de bounces y complaints     │  │
│  └──────────────────────────────────────────────────────┘  │
│                           │                                 │
│                           │ Publica eventos                 │
│                           ▼                                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  Event Destination: sns-tracking-destination         │  │
│  │                                                      │  │
│  │  Eventos rastreados:                                 │  │
│  │  - send                                              │  │
│  │  - delivery                                          │  │
│  │  - open                                              │  │
│  │  - bounce                                            │  │
│  │  - complaint                                         │  │
│  │  - reject                                            │  │
│  │  - renderingFailure                                  │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ Publica a SNS
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Amazon SNS Topic                               │
│         mscorreos-tracking-events-{env}                     │
│                                                             │
│  - Recibe eventos de SES                                   │
│  - Política de acceso para SES                             │
│  - Suscripción HTTPS a MSCorreos                           │
└─────────────────────────────────────────────────────────────┘
                           │
                           │ Notifica vía HTTPS
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              MSCorreos Microservice                         │
│         Endpoint: /sns/notifications                        │
│                                                             │
│  - Recibe eventos de tracking                              │
│  - Valida firma SNS                                        │
│  - Procesa eventos y actualiza BD                          │
│  - Gestiona lista negra automáticamente                    │
└─────────────────────────────────────────────────────────────┘
```

### Flujo de Eventos

1. **Envío de Correo**: MSCorreos envía correo mediante SES con Configuration Set
2. **Generación de Evento**: SES genera eventos (send, delivery, open, bounce, complaint)
3. **Publicación a SNS**: SES publica evento al topic SNS configurado
4. **Notificación HTTPS**: SNS envía evento al endpoint de MSCorreos
5. **Procesamiento**: MSCorreos procesa evento, actualiza BD y lista negra

## Despliegue Automático

### Opción Recomendada: Script de Despliegue

El script `deploy-ses.sh` automatiza todo el proceso de configuración:

```bash
cd MSCorreos/infrastructure/scripts

# Para desarrollo
./deploy-ses.sh dev https://mscorreos-dev.acosux.com/sns/notifications

# Para testing
./deploy-ses.sh test https://mscorreos-test.acosux.com/sns/notifications

# Para producción
./deploy-ses.sh prod https://mscorreos.acosux.com/sns/notifications
```

El script realiza los siguientes pasos:

1. ✅ Despliega SNS Topic para tracking de eventos
2. ✅ Despliega SES Configuration Set con Event Destination
3. ✅ Inicia verificación de dominio e email
4. ✅ Verifica límites de envío actuales
5. ✅ Crea alarmas de CloudWatch
6. ✅ Muestra instrucciones para próximos pasos

### Salida del Script

```
[INFO] Iniciando despliegue de Amazon SES para ambiente: dev
[INFO] Región AWS: us-east-1
[INFO] Paso 1/4: Desplegando SNS Topic para tracking de eventos...
[INFO] Stack SNS desplegado exitosamente
[INFO] SNS Topic ARN: arn:aws:sns:us-east-1:123456789012:mscorreos-tracking-events-dev
[INFO] Paso 2/4: Desplegando SES Configuration Set...
[INFO] Stack SES desplegado exitosamente
[INFO] Configuration Set: mscorreos-tracking-dev
[INFO] Paso 3/4: Verificando identidades de SES...
[WARNING] IMPORTANTE: Debe agregar el siguiente registro TXT a su DNS:
[WARNING] Nombre: _amazonses.documentos-electronicos.info
[WARNING] Valor: abc123xyz...
[WARNING] Tipo: TXT
[INFO] Paso 4/4: Verificando límites de envío de SES...
[INFO] Límites actuales de SES:
[INFO]   - Tasa máxima de envío: 14.0 correos/segundo
[INFO]   - Máximo en 24 horas: 50000 correos
[INFO] ✓ Su cuenta SES tiene acceso de producción

==========================================
DESPLIEGUE COMPLETADO EXITOSAMENTE
==========================================

Recursos creados:
  - SNS Topic: arn:aws:sns:us-east-1:123456789012:mscorreos-tracking-events-dev
  - Configuration Set: mscorreos-tracking-dev
  - Dominio verificado: documentos-electronicos.info
  - Email verificado: notificaciones@documentos-electronicos.info

Próximos pasos:
  1. Agregar registro TXT a DNS para verificar dominio (si aplica)
  2. Confirmar verificación de email desde el buzón
  3. Confirmar suscripción SNS desde el endpoint de MSCorreos
  4. Solicitar salida de sandbox si es necesario
  5. Solicitar aumento de límites si es necesario
```

## Configuración Manual

Si prefiere configurar manualmente o necesita entender los detalles:

### Paso 1: Crear SNS Topic

```bash
# Crear topic
aws sns create-topic \
    --name mscorreos-tracking-events-dev \
    --region us-east-1

# Obtener ARN del topic
SNS_TOPIC_ARN=$(aws sns list-topics --region us-east-1 \
    --query "Topics[?contains(TopicArn, 'mscorreos-tracking-events-dev')].TopicArn" \
    --output text)

echo "SNS Topic ARN: $SNS_TOPIC_ARN"
```

### Paso 2: Configurar Política del Topic

```bash
# Crear archivo de política
cat > sns-policy.json <<EOF
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
      "Resource": "$SNS_TOPIC_ARN",
      "Condition": {
        "StringEquals": {
          "AWS:SourceAccount": "$(aws sts get-caller-identity --query Account --output text)"
        }
      }
    }
  ]
}
EOF

# Aplicar política
aws sns set-topic-attributes \
    --topic-arn $SNS_TOPIC_ARN \
    --attribute-name Policy \
    --attribute-value file://sns-policy.json \
    --region us-east-1
```

### Paso 3: Crear Suscripción HTTPS

```bash
# Suscribir endpoint de MSCorreos
aws sns subscribe \
    --topic-arn $SNS_TOPIC_ARN \
    --protocol https \
    --notification-endpoint https://mscorreos-dev.acosux.com/sns/notifications \
    --region us-east-1

# IMPORTANTE: La suscripción quedará en estado "PendingConfirmation"
# hasta que el endpoint de MSCorreos confirme mediante el SubscribeURL
```

### Paso 4: Crear Configuration Set

```bash
# Crear Configuration Set
aws ses create-configuration-set \
    --configuration-set Name=mscorreos-tracking-dev \
    --region us-east-1

# Habilitar métricas de reputación
aws ses put-configuration-set-reputation-options \
    --configuration-set-name mscorreos-tracking-dev \
    --reputation-metrics-enabled \
    --region us-east-1

# Configurar opciones de envío
aws ses put-configuration-set-sending-options \
    --configuration-set-name mscorreos-tracking-dev \
    --sending-enabled \
    --region us-east-1
```

### Paso 5: Crear Event Destination

```bash
# Crear Event Destination hacia SNS
aws ses create-configuration-set-event-destination \
    --configuration-set-name mscorreos-tracking-dev \
    --event-destination '{
        "Name": "sns-tracking-destination-dev",
        "Enabled": true,
        "MatchingEventTypes": ["send", "delivery", "open", "bounce", "complaint", "reject", "renderingFailure"],
        "SNSDestination": {
            "TopicARN": "'$SNS_TOPIC_ARN'"
        }
    }' \
    --region us-east-1
```

## Verificación de Identidades

### Verificar Dominio

#### Paso 1: Iniciar Verificación

```bash
# Iniciar verificación de dominio
aws ses verify-domain-identity \
    --domain documentos-electronicos.info \
    --region us-east-1

# Obtener token de verificación
VERIFICATION_TOKEN=$(aws ses verify-domain-identity \
    --domain documentos-electronicos.info \
    --region us-east-1 \
    --query 'VerificationToken' \
    --output text)

echo "Token de verificación: $VERIFICATION_TOKEN"
```

#### Paso 2: Agregar Registro DNS

Debe agregar un registro TXT a su DNS:

- **Nombre**: `_amazonses.documentos-electronicos.info`
- **Tipo**: TXT
- **Valor**: `{VERIFICATION_TOKEN}` (obtenido en paso anterior)
- **TTL**: 300 (o el valor por defecto de su proveedor)

#### Paso 3: Verificar Estado

```bash
# Verificar estado de verificación del dominio
aws ses get-identity-verification-attributes \
    --identities documentos-electronicos.info \
    --region us-east-1

# Estado puede ser: Pending, Success, Failed, TemporaryFailure
```

La verificación puede tomar de 5 minutos a 72 horas dependiendo de la propagación DNS.

### Verificar Email Individual

```bash
# Verificar email
aws ses verify-email-identity \
    --email-address notificaciones@documentos-electronicos.info \
    --region us-east-1

# AWS enviará un correo de confirmación al buzón
# El usuario debe hacer clic en el enlace de confirmación
```

### Verificar Estado de Identidades

```bash
# Listar todas las identidades verificadas
aws ses list-identities --region us-east-1

# Ver detalles de verificación
aws ses get-identity-verification-attributes \
    --identities documentos-electronicos.info notificaciones@documentos-electronicos.info \
    --region us-east-1
```

## Salir del Modo Sandbox

Por defecto, las cuentas nuevas de SES están en modo "sandbox", que tiene las siguientes limitaciones:

- ❌ Solo puede enviar correos a direcciones verificadas
- ❌ Solo puede enviar correos desde direcciones verificadas
- ❌ Límite de 200 correos por día
- ❌ Límite de 1 correo por segundo

### Solicitar Acceso de Producción

#### Opción 1: Desde la Consola AWS

1. Ir a [AWS SES Console](https://console.aws.amazon.com/ses/home#/account)
2. En el menú lateral, seleccionar "Account dashboard"
3. En la sección "Sending statistics", hacer clic en "Request production access"
4. Completar el formulario:
   - **Mail Type**: Transactional
   - **Website URL**: https://acosux.com
   - **Use case description**:
     ```
     MSCorreos es un microservicio de notificaciones por correo electrónico
     para el sistema ERP ShrimpSoft. Envía notificaciones transaccionales a
     clientes, proveedores y empleados sobre:
     - Comprobantes electrónicos (facturas, notas de crédito, guías de remisión)
     - Órdenes de compra
     - Roles de pago
     - Estados de cuenta
     - Notificaciones del sistema
     
     Volumen estimado: 10,000 correos/día
     Destinatarios: Clientes, proveedores y empleados de empresas que usan ShrimpSoft
     ```
   - **Compliance**: Confirmar que cumple con políticas anti-spam
   - **Bounce handling**: Confirmar que maneja bounces y complaints
5. Enviar solicitud

#### Opción 2: Desde AWS CLI

```bash
# Crear caso de soporte
aws support create-case \
    --subject "Request to move SES account out of sandbox" \
    --service-code "ses" \
    --category-code "other-account-issues" \
    --communication-body "I would like to move my SES account out of the sandbox to send production emails..." \
    --cc-email-addresses your-email@example.com \
    --language "en" \
    --issue-type "service-limit-increase"
```

### Tiempo de Respuesta

AWS generalmente responde en 24-48 horas. Puede recibir preguntas adicionales sobre su caso de uso.

### Verificar Estado

```bash
# Verificar si tiene acceso de producción
aws sesv2 get-account --region us-east-1 --query 'ProductionAccess' --output text

# Salida:
# true  = Acceso de producción
# false = Modo sandbox
```

## Configuración de Límites

### Límites por Defecto

Después de salir del sandbox, los límites típicos son:

- **Tasa de envío**: 14 correos/segundo
- **Cuota diaria**: 50,000 correos/día

### Verificar Límites Actuales

```bash
# Obtener límites actuales
aws ses get-send-quota --region us-east-1

# Salida:
# {
#     "Max24HourSend": 50000.0,
#     "MaxSendRate": 14.0,
#     "SentLast24Hours": 0.0
# }
```

### Solicitar Aumento de Límites

Si necesita más de 14 correos/segundo o más de 50,000 correos/día:

#### Opción 1: Desde la Consola

1. Ir a [AWS Support Center](https://console.aws.amazon.com/support/home#/case/create?issueType=service-limit-increase)
2. Seleccionar "Service limit increase"
3. Completar:
   - **Limit type**: SES Sending Limits
   - **Region**: US East (N. Virginia)
   - **Limit**: Desired Daily Sending Quota o Desired Maximum Send Rate
   - **New limit value**: Valor deseado (ej: 50 correos/segundo, 200,000 correos/día)
   - **Use case description**: Explicar por qué necesita el aumento
4. Enviar solicitud

#### Opción 2: Aumento Automático

AWS puede aumentar automáticamente sus límites si:
- Mantiene una tasa de bounce baja (< 5%)
- Mantiene una tasa de complaint baja (< 0.1%)
- Envía correos regularmente
- No tiene violaciones de políticas

### Monitorear Uso

```bash
# Ver estadísticas de envío
aws ses get-send-statistics --region us-east-1

# Ver métricas de reputación
aws cloudwatch get-metric-statistics \
    --namespace AWS/SES \
    --metric-name Reputation.BounceRate \
    --dimensions Name=ConfigurationSet,Value=mscorreos-tracking-dev \
    --start-time $(date -u -d '7 days ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 86400 \
    --statistics Average \
    --region us-east-1
```

## Verificación del Despliegue

### 1. Verificar Stacks de CloudFormation

```bash
# Verificar stack SNS
aws cloudformation describe-stacks \
    --stack-name mscorreos-sns-tracking-dev \
    --region us-east-1 \
    --query 'Stacks[0].StackStatus'

# Verificar stack SES
aws cloudformation describe-stacks \
    --stack-name mscorreos-ses-config-dev \
    --region us-east-1 \
    --query 'Stacks[0].StackStatus'

# Ambos deben retornar: "CREATE_COMPLETE" o "UPDATE_COMPLETE"
```

### 2. Verificar Configuration Set

```bash
# Listar Configuration Sets
aws ses list-configuration-sets --region us-east-1

# Ver detalles del Configuration Set
aws ses describe-configuration-set \
    --configuration-set-name mscorreos-tracking-dev \
    --region us-east-1
```

### 3. Verificar Event Destination

```bash
# Ver Event Destinations del Configuration Set
aws ses describe-configuration-set \
    --configuration-set-name mscorreos-tracking-dev \
    --region us-east-1 \
    --query 'EventDestinations'

# Debe mostrar el Event Destination con SNS configurado
```

### 4. Verificar SNS Topic y Suscripción

```bash
# Listar topics SNS
aws sns list-topics --region us-east-1 | grep mscorreos

# Ver suscripciones del topic
SNS_TOPIC_ARN=$(aws sns list-topics --region us-east-1 \
    --query "Topics[?contains(TopicArn, 'mscorreos-tracking-events-dev')].TopicArn" \
    --output text)

aws sns list-subscriptions-by-topic \
    --topic-arn $SNS_TOPIC_ARN \
    --region us-east-1

# Verificar estado de la suscripción (debe ser "Confirmed" después de confirmar desde MSCorreos)
```

### 5. Verificar Identidades

```bash
# Listar identidades verificadas
aws ses list-verified-email-addresses --region us-east-1

# Ver estado de verificación del dominio
aws ses get-identity-verification-attributes \
    --identities documentos-electronicos.info \
    --region us-east-1
```

### 6. Verificar Alarmas de CloudWatch

```bash
# Listar alarmas de MSCorreos
aws cloudwatch describe-alarms \
    --alarm-name-prefix mscorreos-ses \
    --region us-east-1

# Debe mostrar 3 alarmas:
# - mscorreos-ses-high-bounce-rate-dev
# - mscorreos-ses-high-complaint-rate-dev
# - mscorreos-ses-high-reject-rate-dev
```

### 7. Prueba de Envío (Solo si está fuera de sandbox)

```bash
# Enviar correo de prueba
aws ses send-email \
    --from notificaciones@documentos-electronicos.info \
    --destination ToAddresses=test@example.com \
    --message Subject={Data="Test MSCorreos",Charset=utf-8},Body={Text={Data="Este es un correo de prueba",Charset=utf-8}} \
    --configuration-set-name mscorreos-tracking-dev \
    --region us-east-1

# Si está en sandbox, solo puede enviar a direcciones verificadas
```

## Troubleshooting

### Problema: "Configuration Set already exists"

**Causa**: El Configuration Set ya fue creado previamente.

**Solución**:
```bash
# Eliminar Configuration Set existente
aws ses delete-configuration-set \
    --configuration-set-name mscorreos-tracking-dev \
    --region us-east-1

# Volver a ejecutar el script de despliegue
./deploy-ses.sh dev
```

### Problema: "Domain verification pending"

**Causa**: El registro TXT no se ha propagado en DNS.

**Solución**:
1. Verificar que agregó el registro TXT correctamente
2. Esperar propagación DNS (puede tomar hasta 72 horas)
3. Verificar con:
```bash
dig TXT _amazonses.documentos-electronicos.info
# o
nslookup -type=TXT _amazonses.documentos-electronicos.info
```

### Problema: "SNS subscription pending confirmation"

**Causa**: El endpoint de MSCorreos no ha confirmado la suscripción.

**Solución**:
1. Verificar que MSCorreos está corriendo y accesible
2. Verificar logs de MSCorreos para ver si recibió el mensaje de confirmación
3. El endpoint debe responder al mensaje de tipo "SubscriptionConfirmation" con una llamada GET al SubscribeURL

### Problema: "Access Denied" al crear recursos

**Causa**: Permisos IAM insuficientes.

**Solución**:
```bash
# Verificar permisos del usuario actual
aws iam get-user --query 'User.UserName' --output text
aws iam list-attached-user-policies --user-name $(aws iam get-user --query 'User.UserName' --output text)

# Solicitar permisos necesarios al administrador de AWS
```

### Problema: "Bounce rate too high"

**Causa**: Tasa de rebote superior al 5%.

**Solución**:
1. Revisar lista negra y asegurar que no se envía a correos problemáticos
2. Validar formato de emails antes de enviar
3. Limpiar lista de destinatarios
4. Implementar double opt-in para nuevos destinatarios

### Problema: "Complaint rate too high"

**Causa**: Tasa de quejas superior al 0.1%.

**Solución**:
1. Agregar automáticamente a lista negra correos que generan complaints
2. Incluir enlace de "unsubscribe" en todos los correos
3. Enviar solo correos transaccionales relevantes
4. No enviar correos masivos de marketing

### Problema: "Sending paused"

**Causa**: AWS pausó el envío por violación de políticas.

**Solución**:
1. Revisar notificaciones de AWS en el email de la cuenta
2. Contactar a AWS Support para entender la causa
3. Corregir el problema identificado
4. Solicitar reactivación del servicio

### Problema: "Rate limit exceeded"

**Causa**: Superó el límite de 14 correos/segundo.

**Solución**:
1. Implementar throttling en la aplicación
2. Usar colas SQS para controlar tasa de envío
3. Solicitar aumento de límite a AWS Support

## Configuración de la Aplicación

Después del despliegue, configure las siguientes variables en MSCorreos:

### application.yml

```yaml
aws:
  region: us-east-1
  
  ses:
    configuration-set: mscorreos-tracking-${ENVIRONMENT}
    from-email: notificaciones@documentos-electronicos.info
    from-name: "Sistema MSCorreos"
    rate-limit: 14  # correos/segundo
    
  sns:
    tracking-topic-arn: ${SNS_TOPIC_ARN}
    endpoint: /sns/notifications
```

### Variables de Entorno

```bash
# .env
AWS_REGION=us-east-1
SES_CONFIGURATION_SET=mscorreos-tracking-dev
SES_FROM_EMAIL=notificaciones@documentos-electronicos.info
SNS_TOPIC_ARN=arn:aws:sns:us-east-1:123456789012:mscorreos-tracking-events-dev
```

## Próximos Pasos

Después de completar la configuración de SES:

1. ✅ **Confirmar suscripción SNS** desde el endpoint de MSCorreos
2. ✅ **Implementar SNS Listener** en MSCorreos (Task 2.3)
3. ✅ **Implementar SES Adapter** para envío de correos (Task 2.4)
4. ✅ **Configurar plantillas** de correo en la base de datos
5. ✅ **Probar envío** de correos en ambiente de desarrollo
6. ✅ **Solicitar salida de sandbox** para producción
7. ✅ **Monitorear métricas** de bounce y complaint

## Referencias

- [AWS SES Documentation](https://docs.aws.amazon.com/ses/)
- [SES Configuration Sets](https://docs.aws.amazon.com/ses/latest/dg/using-configuration-sets.html)
- [SES Event Publishing](https://docs.aws.amazon.com/ses/latest/dg/event-publishing.html)
- [SNS HTTPS Subscriptions](https://docs.aws.amazon.com/sns/latest/dg/sns-http-https-endpoint-as-subscriber.html)
- [SES Sending Limits](https://docs.aws.amazon.com/ses/latest/dg/manage-sending-quotas.html)
- [SES Best Practices](https://docs.aws.amazon.com/ses/latest/dg/best-practices.html)

## Soporte

Para problemas o preguntas:
- Revisar logs de CloudFormation
- Consultar documentación de AWS SES
- Contactar al equipo de DevOps/Infraestructura
- Abrir caso en AWS Support si es necesario
