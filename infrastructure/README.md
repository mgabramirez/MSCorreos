# Infraestructura AWS para MSCorreos

Este directorio contiene la configuración de infraestructura como código (IaC) para el sistema de notificaciones por correo electrónico MSCorreos.

## Estructura

```
infrastructure/
├── cloudformation/
│   ├── sqs-queues.yaml          # Definición de colas SQS
│   ├── sns-topic.yaml           # Topic SNS para tracking de SES
│   └── ses-configuration.yaml   # Configuration Set de SES
├── scripts/
│   ├── deploy-sqs.sh            # Script de despliegue SQS
│   ├── delete-sqs.sh            # Script de eliminación SQS
│   ├── deploy-ses.sh            # Script de despliegue SES
│   └── delete-ses.sh            # Script de eliminación SES
├── DEPLOYMENT_GUIDE.md          # Guía de despliegue SQS
├── SES_CONFIGURATION_GUIDE.md   # Guía completa de configuración SES
├── SES_QUICK_REFERENCE.md       # Referencia rápida SES
├── TASK_2.1_SUMMARY.md          # Resumen Task 2.1 (SQS)
├── TASK_2.2_SUMMARY.md          # Resumen Task 2.2 (SES)
└── README.md                     # Este archivo
```

## Componentes de Infraestructura

### Colas SQS (Task 2.1)

El sistema utiliza tres colas SQS:

1. **Cola Standard** (`mscorreos-standard-{env}`)
   - Para mensajes de prioridad MEDIA y BAJA
   - Configuración:
     - Visibility Timeout: 30 segundos
     - Message Retention: 14 días
     - Long Polling: 20 segundos
     - Max Receive Count: 3 (antes de mover a DLQ)

2. **Cola FIFO** (`mscorreos-alta-prioridad-{env}.fifo`)
   - Para mensajes de prioridad ALTA
   - Garantiza orden FIFO y deduplicación
   - Configuración:
     - Visibility Timeout: 30 segundos
     - Message Retention: 14 días
     - Long Polling: 20 segundos
     - Max Receive Count: 3 (antes de mover a DLQ)
     - Content-Based Deduplication: Habilitado

3. **Dead Letter Queue** (`mscorreos-dlq-{env}`)
   - Recibe mensajes que fallaron después de 3 reintentos
   - Message Retention: 14 días
   - Alarma CloudWatch cuando contiene más de 10 mensajes

### Alarmas CloudWatch (SQS)

- **DLQ Alarm**: Se activa cuando la DLQ tiene más de 10 mensajes
- **Standard Queue Depth**: Se activa cuando la cola Standard tiene más de 1000 mensajes
- **FIFO Queue Depth**: Se activa cuando la cola FIFO tiene más de 500 mensajes

### Amazon SES (Task 2.2)

El sistema utiliza Amazon SES para envío de correos con tracking completo:

1. **Configuration Set** (`mscorreos-tracking-{env}`)
   - Tracking de eventos habilitado
   - Métricas de reputación habilitadas
   - Supresión automática de bounces y complaints
   - Event Destination configurado hacia SNS

2. **SNS Topic** (`mscorreos-tracking-events-{env}`)
   - Recibe eventos de SES (send, delivery, open, bounce, complaint, reject, renderingFailure)
   - Política de acceso para SES
   - Suscripción HTTPS al endpoint de MSCorreos

3. **Identidades Verificadas**
   - Dominio: `documentos-electronicos.info`
   - Email: `notificaciones@documentos-electronicos.info`

### Alarmas CloudWatch (SES)

- **High Bounce Rate**: Se activa cuando bounce rate > 5%
- **High Complaint Rate**: Se activa cuando complaint rate > 0.1%
- **High Reject Rate**: Se activa cuando hay > 10 rechazos en 5 minutos
- **Failed SNS Notifications**: Se activa cuando SNS no puede entregar > 5 mensajes

## Requisitos Previos

1. **AWS CLI** instalado y configurado
   ```bash
   aws --version
   aws configure
   ```

2. **Permisos IAM** necesarios:
   - `cloudformation:*`
   - `sqs:*`
   - `cloudwatch:*`
   - `iam:CreateRole`
   - `iam:AttachRolePolicy`

3. **Región AWS**: us-east-1 (configurada por defecto)

## Despliegue

### Desplegar Infraestructura Completa

Para desplegar toda la infraestructura (SQS + SES):

```bash
cd infrastructure/scripts

# 1. Desplegar colas SQS
./deploy-sqs.sh dev

# 2. Desplegar SES (requiere endpoint de MSCorreos)
./deploy-ses.sh dev https://mscorreos-dev.acosux.com/sns/notifications
```

### Desplegar Solo Colas SQS

```bash
cd infrastructure/scripts
chmod +x deploy-sqs.sh
./deploy-sqs.sh [dev|test|prod]
```

Ejemplo para ambiente de desarrollo:
```bash
./deploy-sqs.sh dev
```

El script:
1. Valida el template de CloudFormation
2. Crea o actualiza el stack según corresponda
3. Espera a que el despliegue se complete
4. Muestra los outputs del stack (URLs y ARNs de las colas)

### Verificar Despliegue

```bash
# Ver estado del stack
aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1

# Ver outputs del stack
aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs'

# Listar colas SQS
aws sqs list-queues --region us-east-1 | grep mscorreos
```

### Eliminar Infraestructura

```bash
cd infrastructure/scripts
chmod +x delete-sqs.sh
./delete-sqs.sh [dev|test|prod]
```

**⚠️ ADVERTENCIA**: Esta operación eliminará todas las colas y los mensajes que contengan.

### Desplegar Amazon SES

```bash
cd infrastructure/scripts
chmod +x deploy-ses.sh
./deploy-ses.sh [dev|test|prod] [mscorreos-endpoint-url]
```

Ejemplo para ambiente de desarrollo:
```bash
./deploy-ses.sh dev https://mscorreos-dev.acosux.com/sns/notifications
```

El script:
1. Despliega SNS Topic para tracking de eventos
2. Despliega SES Configuration Set con Event Destination
3. Inicia verificación de dominio e email
4. Verifica límites de envío actuales
5. Muestra instrucciones para próximos pasos

**Documentación detallada**: Ver [SES_CONFIGURATION_GUIDE.md](./SES_CONFIGURATION_GUIDE.md)

**Referencia rápida**: Ver [SES_QUICK_REFERENCE.md](./SES_QUICK_REFERENCE.md)

### Eliminar Amazon SES

```bash
cd infrastructure/scripts
chmod +x delete-ses.sh
./delete-ses.sh [dev|test|prod]
```

**⚠️ ADVERTENCIA**: Esta operación eliminará la configuración de SES pero NO las identidades verificadas.

## Configuración de la Aplicación

Después del despliegue, configure las siguientes variables de entorno en la aplicación MSCorreos:

```properties
# application.properties o application.yml

# Cola Standard (prioridad MEDIA/BAJA)
aws.sqs.queue.standard.url=${STANDARD_QUEUE_URL}
aws.sqs.queue.standard.name=mscorreos-standard-${ENVIRONMENT}

# Cola FIFO (prioridad ALTA)
aws.sqs.queue.fifo.url=${FIFO_QUEUE_URL}
aws.sqs.queue.fifo.name=mscorreos-alta-prioridad-${ENVIRONMENT}.fifo

# Dead Letter Queue
aws.sqs.queue.dlq.url=${DLQ_URL}
aws.sqs.queue.dlq.name=mscorreos-dlq-${ENVIRONMENT}

# Configuración de región
aws.region=us-east-1

# Configuración de polling
aws.sqs.max-number-of-messages=10
aws.sqs.wait-time-seconds=20
aws.sqs.visibility-timeout=30
```

## Obtener URLs de las Colas

Después del despliegue, puede obtener las URLs de las colas con:

```bash
# URL de la cola Standard
aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`StandardQueueURL`].OutputValue' \
    --output text

# URL de la cola FIFO
aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`FifoQueueURL`].OutputValue' \
    --output text

# URL de la DLQ
aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`DeadLetterQueueURL`].OutputValue' \
    --output text
```

## Monitoreo

### Ver Métricas de las Colas

```bash
# Número de mensajes visibles en la cola Standard
aws cloudwatch get-metric-statistics \
    --namespace AWS/SQS \
    --metric-name ApproximateNumberOfMessagesVisible \
    --dimensions Name=QueueName,Value=mscorreos-standard-dev \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Average \
    --region us-east-1
```

### Ver Alarmas

```bash
# Listar alarmas relacionadas con MSCorreos
aws cloudwatch describe-alarms \
    --alarm-name-prefix mscorreos \
    --region us-east-1
```

## Troubleshooting

### Error: "Stack already exists"

Si el stack ya existe y desea recrearlo:
```bash
./delete-sqs.sh dev
./deploy-sqs.sh dev
```

### Error: "Insufficient permissions"

Verifique que su usuario/rol de IAM tenga los permisos necesarios listados en "Requisitos Previos".

### Mensajes atascados en la DLQ

Para reprocesar mensajes de la DLQ:

1. Inspeccionar mensajes:
```bash
aws sqs receive-message \
    --queue-url <DLQ_URL> \
    --max-number-of-messages 10 \
    --region us-east-1
```

2. Mover mensajes de vuelta a la cola principal (requiere script personalizado o herramienta externa)

### Purgar Cola (solo para desarrollo/testing)

```bash
# ⚠️ CUIDADO: Esto elimina TODOS los mensajes de la cola
aws sqs purge-queue \
    --queue-url <QUEUE_URL> \
    --region us-east-1
```

## Costos Estimados

Los costos de SQS son muy bajos:
- Primeros 1 millón de requests/mes: GRATIS
- Después: $0.40 por millón de requests
- CloudWatch Alarms: $0.10 por alarma/mes

**Estimación mensual para ambiente de producción**: < $5 USD

## Referencias

- [Guía de Despliegue SQS](./DEPLOYMENT_GUIDE.md)
- [Guía de Configuración SES](./SES_CONFIGURATION_GUIDE.md)
- [Referencia Rápida SES](./SES_QUICK_REFERENCE.md)
- [Resumen Task 2.1 (SQS)](./TASK_2.1_SUMMARY.md)
- [Resumen Task 2.2 (SES)](./TASK_2.2_SUMMARY.md)
- [AWS SQS Documentation](https://docs.aws.amazon.com/sqs/)
- [AWS SES Documentation](https://docs.aws.amazon.com/ses/)
- [AWS SNS Documentation](https://docs.aws.amazon.com/sns/)
- [AWS CloudFormation SQS Reference](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-sqs-queue.html)
- [SQS Best Practices](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-best-practices.html)
- [SES Best Practices](https://docs.aws.amazon.com/ses/latest/dg/best-practices.html)

## Soporte

Para problemas o preguntas, contactar al equipo de desarrollo de MSCorreos.
