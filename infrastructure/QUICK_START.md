# Quick Start - Configuración SQS para MSCorreos

Guía rápida para desarrolladores que necesitan desplegar la infraestructura SQS.

## TL;DR

```bash
# 1. Configurar AWS CLI
aws configure

# 2. Desplegar con CloudFormation
cd MSCorreos/infrastructure/scripts
./deploy-sqs.sh dev

# 3. Obtener URLs
aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
    --output table

# 4. Configurar aplicación
# Copiar las URLs a tu archivo .env o application.yml
```

## Opción 1: CloudFormation (5 minutos)

### Paso 1: Validar credenciales
```bash
aws sts get-caller-identity
```

### Paso 2: Desplegar
```bash
cd MSCorreos/infrastructure/scripts
chmod +x deploy-sqs.sh  # Solo en Linux/macOS
./deploy-sqs.sh dev
```

### Paso 3: Obtener URLs
```bash
# Guardar en variables de entorno
export STANDARD_QUEUE_URL=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`StandardQueueURL`].OutputValue' \
    --output text)

export FIFO_QUEUE_URL=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`FifoQueueURL`].OutputValue' \
    --output text)

export DLQ_URL=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`DeadLetterQueueURL`].OutputValue' \
    --output text)

echo "Standard: $STANDARD_QUEUE_URL"
echo "FIFO: $FIFO_QUEUE_URL"
echo "DLQ: $DLQ_URL"
```

## Opción 2: Terraform (5 minutos)

### Paso 1: Inicializar
```bash
cd MSCorreos/infrastructure/terraform
terraform init
```

### Paso 2: Configurar variables
```bash
cat > terraform.tfvars <<EOF
environment = "dev"
aws_region  = "us-east-1"
EOF
```

### Paso 3: Desplegar
```bash
terraform apply -auto-approve
```

### Paso 4: Obtener URLs
```bash
terraform output
```

## Configurar Aplicación

### Crear archivo .env
```bash
cat > MSCorreos/.env <<EOF
SPRING_PROFILES_ACTIVE=dev
AWS_REGION=us-east-1
STANDARD_QUEUE_URL=$STANDARD_QUEUE_URL
FIFO_QUEUE_URL=$FIFO_QUEUE_URL
DLQ_URL=$DLQ_URL
EOF
```

### O actualizar application.yml
```yaml
aws:
  region: us-east-1
  sqs:
    queue:
      standard:
        url: ${STANDARD_QUEUE_URL}
      fifo:
        url: ${FIFO_QUEUE_URL}
      dlq:
        url: ${DLQ_URL}
```

## Verificar Despliegue

### Test rápido
```bash
# Enviar mensaje
aws sqs send-message \
    --queue-url $STANDARD_QUEUE_URL \
    --message-body '{"test": "hola mundo"}' \
    --region us-east-1

# Recibir mensaje
aws sqs receive-message \
    --queue-url $STANDARD_QUEUE_URL \
    --max-number-of-messages 1 \
    --region us-east-1
```

## Troubleshooting Rápido

### Error: "Access Denied"
```bash
# Verificar permisos
aws iam get-user
aws iam list-attached-user-policies --user-name YOUR_USERNAME
```

### Error: "Stack already exists"
```bash
# Eliminar y recrear
./delete-sqs.sh dev
./deploy-sqs.sh dev
```

### Ver logs de CloudFormation
```bash
aws cloudformation describe-stack-events \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --max-items 10
```

## Limpiar Todo

### CloudFormation
```bash
./delete-sqs.sh dev
```

### Terraform
```bash
terraform destroy -auto-approve
```

## Próximos Pasos

1. ✅ Colas SQS configuradas
2. ⏭️ Implementar SQS Consumer (Task 2.2)
3. ⏭️ Configurar Amazon SES (Task 2.3)
4. ⏭️ Implementar SNS Listener (Task 2.4)

## Ayuda

- Documentación completa: `DEPLOYMENT_GUIDE.md`
- Resumen de la tarea: `TASK_2.1_SUMMARY.md`
- README principal: `README.md`

## Comandos Útiles

```bash
# Listar colas
aws sqs list-queues --region us-east-1

# Ver atributos de cola
aws sqs get-queue-attributes \
    --queue-url $STANDARD_QUEUE_URL \
    --attribute-names All

# Ver alarmas
aws cloudwatch describe-alarms --alarm-name-prefix mscorreos

# Purgar cola (CUIDADO: elimina todos los mensajes)
aws sqs purge-queue --queue-url $STANDARD_QUEUE_URL

# Ver métricas
aws cloudwatch get-metric-statistics \
    --namespace AWS/SQS \
    --metric-name ApproximateNumberOfMessagesVisible \
    --dimensions Name=QueueName,Value=mscorreos-standard-dev \
    --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
    --period 300 \
    --statistics Average
```

---

**¿Problemas?** Revisa `DEPLOYMENT_GUIDE.md` para troubleshooting detallado.
