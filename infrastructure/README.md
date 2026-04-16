# Infraestructura AWS para MSCorreos

Este directorio contiene la configuración de infraestructura como código (IaC) para el sistema de notificaciones por correo electrónico MSCorreos.

## Estructura

```
infrastructure/
├── cloudformation/
│   └── sqs-queues.yaml          # Definición de colas SQS
├── scripts/
│   ├── deploy-sqs.sh            # Script de despliegue
│   └── delete-sqs.sh            # Script de eliminación
└── README.md                     # Este archivo
```

## Componentes de Infraestructura

### Colas SQS

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

### Alarmas CloudWatch

- **DLQ Alarm**: Se activa cuando la DLQ tiene más de 10 mensajes
- **Standard Queue Depth**: Se activa cuando la cola Standard tiene más de 1000 mensajes
- **FIFO Queue Depth**: Se activa cuando la cola FIFO tiene más de 500 mensajes

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

### Desplegar Colas SQS

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

- [AWS SQS Documentation](https://docs.aws.amazon.com/sqs/)
- [AWS CloudFormation SQS Reference](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-resource-sqs-queue.html)
- [SQS Best Practices](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-best-practices.html)

## Soporte

Para problemas o preguntas, contactar al equipo de desarrollo de MSCorreos.
