# Diagrama de Arquitectura - Colas SQS MSCorreos

## Vista General de la Infraestructura

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         PRODUCTORES (ShrimpSoftServer)                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐│
│  │   Módulo     │  │   Módulo     │  │   Módulo     │  │   Módulo     ││
│  │   Cartera    │  │  Inventario  │  │     RRHH     │  │ Contabilidad ││
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘│
│         │                 │                 │                 │         │
└─────────┼─────────────────┼─────────────────┼─────────────────┼─────────┘
          │                 │                 │                 │
          │ Prioridad       │ Prioridad       │ Prioridad       │ Prioridad
          │ MEDIA/BAJA      │ MEDIA/BAJA      │ ALTA            │ MEDIA/BAJA
          │                 │                 │                 │
          ▼                 ▼                 ▼                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           AWS SQS INFRASTRUCTURE                         │
│                                                                          │
│  ┌────────────────────────────────────┐  ┌──────────────────────────┐  │
│  │   Cola Standard                    │  │   Cola FIFO              │  │
│  │   mscorreos-standard-{env}         │  │   mscorreos-alta-        │  │
│  │                                    │  │   prioridad-{env}.fifo   │  │
│  │   • Prioridad: MEDIA/BAJA          │  │                          │  │
│  │   • Visibility: 30s                │  │   • Prioridad: ALTA      │  │
│  │   • Retention: 14 días             │  │   • Visibility: 30s      │  │
│  │   • Long Polling: 20s              │  │   • Retention: 14 días   │  │
│  │   • Max Receive: 3                 │  │   • Long Polling: 20s    │  │
│  │   • Batch Size: 10                 │  │   • Max Receive: 3       │  │
│  └────────────┬───────────────────────┘  └──────────┬───────────────┘  │
│               │                                      │                  │
│               │ Después de 3 reintentos              │                  │
│               │                                      │                  │
│               ▼                                      ▼                  │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │   Dead Letter Queue (DLQ)                                      │    │
│  │   mscorreos-dlq-{env}                                          │    │
│  │                                                                 │    │
│  │   • Mensajes fallidos después de 3 reintentos                  │    │
│  │   • Retention: 14 días                                         │    │
│  │   • Alarma CloudWatch: > 10 mensajes                           │    │
│  └────────────────────────────────────────────────────────────────┘    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Long Polling (20s)
                                    │ Batch Size: 10 mensajes
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      CONSUMIDOR (MSCorreos Microservice)                 │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │   SQS Consumer                                                  │    │
│  │   • Polling de mensajes                                         │    │
│  │   • Procesamiento en lotes (10 mensajes)                        │    │
│  │   • Validación de lista negra                                   │    │
│  │   • Envío mediante Amazon SES                                   │    │
│  │   • Registro en BD (cor_notificaciones)                         │    │
│  └────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

## Flujo de Mensajes

### 1. Envío Normal (Éxito)

```
Productor → Cola SQS → Consumer → Procesar → SES → Eliminar de Cola
                                                      (ACK)
```

### 2. Envío con Reintento (Error Temporal)

```
Productor → Cola SQS → Consumer → Error Temporal
                          ↓
                    Reintento 1 (1s delay)
                          ↓
                    Reintento 2 (2s delay)
                          ↓
                    Reintento 3 (4s delay)
                          ↓
                       Éxito → Eliminar de Cola
```

### 3. Envío Fallido (Error Permanente)

```
Productor → Cola SQS → Consumer → Error Permanente
                          ↓
                    Reintento 1
                          ↓
                    Reintento 2
                          ↓
                    Reintento 3
                          ↓
                    Mover a DLQ → Alarma CloudWatch
```

## Configuración de Colas

### Cola Standard (MEDIA/BAJA)

| Parámetro | Valor | Requisito |
|-----------|-------|-----------|
| Visibility Timeout | 30 segundos | Req 2.5 |
| Message Retention | 14 días | Req 6.7 |
| Long Polling | 20 segundos | Design |
| Max Receive Count | 3 | Req 2.4, 6.2 |
| Batch Size | 10 mensajes | Design |
| Redrive Policy | → DLQ | Req 2.4 |

### Cola FIFO (ALTA)

| Parámetro | Valor | Requisito |
|-----------|-------|-----------|
| FIFO Queue | Habilitado | Design |
| Content Deduplication | Habilitado | Design |
| Visibility Timeout | 30 segundos | Req 2.5 |
| Message Retention | 14 días | Req 6.7 |
| Long Polling | 20 segundos | Design |
| Max Receive Count | 3 | Req 2.4, 6.2 |
| Batch Size | 10 mensajes | Design |
| Redrive Policy | → DLQ | Req 2.4 |

### Dead Letter Queue

| Parámetro | Valor | Requisito |
|-----------|-------|-----------|
| Message Retention | 14 días | Req 6.7 |
| Visibility Timeout | 30 segundos | - |
| Alarma CloudWatch | > 10 mensajes | Req 8.3 |

## Alarmas CloudWatch

```
┌─────────────────────────────────────────────────────────────┐
│                    CloudWatch Alarms                         │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  DLQ Alarm                                          │    │
│  │  • Métrica: ApproximateNumberOfMessagesVisible      │    │
│  │  • Threshold: > 10 mensajes                         │    │
│  │  • Period: 5 minutos                                │    │
│  │  • Action: Notificar equipo                         │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  Standard Queue Depth Alarm                         │    │
│  │  • Métrica: ApproximateNumberOfMessagesVisible      │    │
│  │  • Threshold: > 1000 mensajes                       │    │
│  │  • Period: 5 minutos                                │    │
│  │  • Action: Escalar consumidores                     │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  FIFO Queue Depth Alarm                             │    │
│  │  • Métrica: ApproximateNumberOfMessagesVisible      │    │
│  │  • Threshold: > 500 mensajes                        │    │
│  │  • Period: 5 minutos                                │    │
│  │  • Action: Escalar consumidores                     │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

## Políticas de Acceso IAM

### Productor (ShrimpSoftServer)

```json
{
  "Effect": "Allow",
  "Action": [
    "sqs:SendMessage",
    "sqs:GetQueueUrl"
  ],
  "Resource": [
    "arn:aws:sqs:us-east-1:*:mscorreos-standard-*",
    "arn:aws:sqs:us-east-1:*:mscorreos-alta-prioridad-*.fifo"
  ]
}
```

### Consumidor (MSCorreos)

```json
{
  "Effect": "Allow",
  "Action": [
    "sqs:ReceiveMessage",
    "sqs:DeleteMessage",
    "sqs:GetQueueAttributes",
    "sqs:SendMessage"
  ],
  "Resource": [
    "arn:aws:sqs:us-east-1:*:mscorreos-standard-*",
    "arn:aws:sqs:us-east-1:*:mscorreos-alta-prioridad-*.fifo",
    "arn:aws:sqs:us-east-1:*:mscorreos-dlq-*"
  ]
}
```

## Escalabilidad

```
Carga Baja                  Carga Media                 Carga Alta
(< 100 msgs)                (100-1000 msgs)             (> 1000 msgs)

┌──────────┐                ┌──────────┐                ┌──────────┐
│Consumer 1│                │Consumer 1│                │Consumer 1│
└──────────┘                ├──────────┤                ├──────────┤
                            │Consumer 2│                │Consumer 2│
                            ├──────────┤                ├──────────┤
                            │Consumer 3│                │Consumer 3│
                            └──────────┘                ├──────────┤
                                                        │Consumer 4│
                                                        ├──────────┤
                                                        │Consumer 5│
                                                        ├──────────┤
                                                        │   ...    │
                                                        ├──────────┤
                                                        │Consumer10│
                                                        └──────────┘

1 instancia                 3 instancias                10 instancias
```

## Métricas Clave

| Métrica | Descripción | Threshold |
|---------|-------------|-----------|
| ApproximateNumberOfMessagesVisible | Mensajes en cola | < 1000 |
| ApproximateAgeOfOldestMessage | Edad del mensaje más antiguo | < 300s |
| NumberOfMessagesSent | Mensajes enviados | - |
| NumberOfMessagesReceived | Mensajes recibidos | - |
| NumberOfMessagesDeleted | Mensajes procesados | - |
| ApproximateNumberOfMessagesNotVisible | Mensajes en procesamiento | - |

## Costos Estimados

```
┌─────────────────────────────────────────────────────────┐
│  Componente              │  Costo Mensual (USD)         │
├─────────────────────────────────────────────────────────┤
│  SQS Requests            │  $0.00 - $5.00               │
│  (primeros 1M gratis)    │                              │
├─────────────────────────────────────────────────────────┤
│  CloudWatch Alarms (3)   │  $0.30                       │
├─────────────────────────────────────────────────────────┤
│  CloudWatch Logs         │  $0.50 - $2.00               │
├─────────────────────────────────────────────────────────┤
│  TOTAL                   │  $0.80 - $7.30               │
└─────────────────────────────────────────────────────────┘
```

## Ambientes

```
┌──────────────────────────────────────────────────────────────┐
│  Ambiente  │  Stack Name           │  Colas                  │
├──────────────────────────────────────────────────────────────┤
│  dev       │  mscorreos-sqs-dev    │  mscorreos-*-dev        │
│  test      │  mscorreos-sqs-test   │  mscorreos-*-test       │
│  prod      │  mscorreos-sqs-prod   │  mscorreos-*-prod       │
└──────────────────────────────────────────────────────────────┘
```

## Referencias

- **AWS SQS**: https://docs.aws.amazon.com/sqs/
- **CloudWatch**: https://docs.aws.amazon.com/cloudwatch/
- **IAM Policies**: https://docs.aws.amazon.com/iam/
- **Best Practices**: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-best-practices.html
