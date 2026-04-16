# Guía de Despliegue - Infraestructura SQS para MSCorreos

Esta guía describe el proceso completo para desplegar la infraestructura de colas SQS para el sistema de notificaciones MSCorreos.

## Tabla de Contenidos

1. [Requisitos Previos](#requisitos-previos)
2. [Opción A: Despliegue con CloudFormation](#opción-a-despliegue-con-cloudformation)
3. [Opción B: Despliegue con Terraform](#opción-b-despliegue-con-terraform)
4. [Configuración de la Aplicación](#configuración-de-la-aplicación)
5. [Verificación del Despliegue](#verificación-del-despliegue)
6. [Configuración de IAM Roles](#configuración-de-iam-roles)
7. [Troubleshooting](#troubleshooting)

## Requisitos Previos

### 1. Herramientas Necesarias

- **AWS CLI** (versión 2.x o superior)
  ```bash
  # Instalar en Linux/macOS
  curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
  unzip awscliv2.zip
  sudo ./aws/install
  
  # Verificar instalación
  aws --version
  ```

- **CloudFormation** (incluido con AWS CLI) o **Terraform** (versión 1.0+)
  ```bash
  # Instalar Terraform en Linux
  wget https://releases.hashicorp.com/terraform/1.6.0/terraform_1.6.0_linux_amd64.zip
  unzip terraform_1.6.0_linux_amd64.zip
  sudo mv terraform /usr/local/bin/
  
  # Verificar instalación
  terraform --version
  ```

### 2. Configurar Credenciales AWS

```bash
aws configure
```

Proporcionar:
- AWS Access Key ID
- AWS Secret Access Key
- Default region: `us-east-1`
- Default output format: `json`

### 3. Verificar Permisos IAM

El usuario/rol debe tener los siguientes permisos:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:*",
        "cloudformation:*",
        "cloudwatch:*",
        "iam:CreateRole",
        "iam:AttachRolePolicy",
        "iam:GetRole",
        "iam:PassRole"
      ],
      "Resource": "*"
    }
  ]
}
```

## Opción A: Despliegue con CloudFormation

### Paso 1: Validar Template

```bash
cd MSCorreos/infrastructure/cloudformation
aws cloudformation validate-template \
    --template-body file://sqs-queues.yaml \
    --region us-east-1
```

### Paso 2: Desplegar Stack

#### Para Desarrollo:
```bash
cd ../scripts
chmod +x deploy-sqs.sh
./deploy-sqs.sh dev
```

#### Para Testing:
```bash
./deploy-sqs.sh test
```

#### Para Producción:
```bash
./deploy-sqs.sh prod
```

### Paso 3: Verificar Despliegue

```bash
# Ver estado del stack
aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1

# Ver outputs
aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
    --output table
```

### Paso 4: Obtener URLs de las Colas

```bash
# Cola Standard
export STANDARD_QUEUE_URL=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`StandardQueueURL`].OutputValue' \
    --output text)

# Cola FIFO
export FIFO_QUEUE_URL=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`FifoQueueURL`].OutputValue' \
    --output text)

# DLQ
export DLQ_URL=$(aws cloudformation describe-stacks \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1 \
    --query 'Stacks[0].Outputs[?OutputKey==`DeadLetterQueueURL`].OutputValue' \
    --output text)

echo "Standard Queue: $STANDARD_QUEUE_URL"
echo "FIFO Queue: $FIFO_QUEUE_URL"
echo "DLQ: $DLQ_URL"
```

## Opción B: Despliegue con Terraform

### Paso 1: Inicializar Terraform

```bash
cd MSCorreos/infrastructure/terraform
terraform init
```

### Paso 2: Crear Archivo de Variables

```bash
cp terraform.tfvars.example terraform.tfvars
```

Editar `terraform.tfvars`:
```hcl
environment = "dev"  # Cambiar según ambiente
aws_region  = "us-east-1"
```

### Paso 3: Planificar Cambios

```bash
terraform plan
```

Revisar los recursos que se crearán.

### Paso 4: Aplicar Configuración

```bash
terraform apply
```

Confirmar con `yes`.

### Paso 5: Obtener Outputs

```bash
# Ver todos los outputs
terraform output

# Obtener URLs específicas
export STANDARD_QUEUE_URL=$(terraform output -raw standard_queue_url)
export FIFO_QUEUE_URL=$(terraform output -raw fifo_queue_url)
export DLQ_URL=$(terraform output -raw dlq_url)

echo "Standard Queue: $STANDARD_QUEUE_URL"
echo "FIFO Queue: $FIFO_QUEUE_URL"
echo "DLQ: $DLQ_URL"
```

## Configuración de la Aplicación

### Paso 1: Actualizar application.yml

Copiar el archivo de configuración:
```bash
cp infrastructure/config/application-sqs.yml src/main/resources/
```

### Paso 2: Configurar Variables de Entorno

Crear archivo `.env` en la raíz del proyecto:

```bash
# .env
SPRING_PROFILES_ACTIVE=dev
AWS_REGION=us-east-1

# URLs de las colas (obtenidas del despliegue)
STANDARD_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/123456789012/mscorreos-standard-dev
FIFO_QUEUE_URL=https://sqs.us-east-1.amazonaws.com/123456789012/mscorreos-alta-prioridad-dev.fifo
DLQ_URL=https://sqs.us-east-1.amazonaws.com/123456789012/mscorreos-dlq-dev

# Credenciales AWS (si no se usan roles de IAM)
AWS_ACCESS_KEY_ID=your_access_key
AWS_SECRET_ACCESS_KEY=your_secret_key
```

**⚠️ IMPORTANTE**: Agregar `.env` al `.gitignore` para no versionar credenciales.

### Paso 3: Actualizar pom.xml

Asegurar que las dependencias de AWS estén incluidas:

```xml
<dependencies>
    <!-- AWS SDK for Java 2.x -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>sqs</artifactId>
        <version>2.20.0</version>
    </dependency>
    
    <!-- Spring Cloud AWS -->
    <dependency>
        <groupId>io.awspring.cloud</groupId>
        <artifactId>spring-cloud-aws-messaging</artifactId>
        <version>3.0.0</version>
    </dependency>
</dependencies>
```

## Verificación del Despliegue

### 1. Verificar que las Colas Existen

```bash
# Listar todas las colas
aws sqs list-queues --region us-east-1

# Verificar cola específica
aws sqs get-queue-attributes \
    --queue-url $STANDARD_QUEUE_URL \
    --attribute-names All \
    --region us-east-1
```

### 2. Enviar Mensaje de Prueba

```bash
# Enviar mensaje a cola Standard
aws sqs send-message \
    --queue-url $STANDARD_QUEUE_URL \
    --message-body '{"test": "mensaje de prueba"}' \
    --region us-east-1

# Recibir mensaje
aws sqs receive-message \
    --queue-url $STANDARD_QUEUE_URL \
    --max-number-of-messages 1 \
    --region us-east-1
```

### 3. Verificar Alarmas CloudWatch

```bash
# Listar alarmas
aws cloudwatch describe-alarms \
    --alarm-name-prefix mscorreos \
    --region us-east-1
```

### 4. Verificar Métricas

```bash
# Ver número de mensajes en cola
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

## Configuración de IAM Roles

### Para ECS Tasks

Crear un rol de IAM para las tareas ECS:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
        "sqs:SendMessage"
      ],
      "Resource": [
        "arn:aws:sqs:us-east-1:ACCOUNT_ID:mscorreos-standard-*",
        "arn:aws:sqs:us-east-1:ACCOUNT_ID:mscorreos-alta-prioridad-*.fifo",
        "arn:aws:sqs:us-east-1:ACCOUNT_ID:mscorreos-dlq-*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      "Resource": "arn:aws:logs:*:*:*"
    }
  ]
}
```

### Para Productores (ShrimpSoftServer)

Crear un rol con permisos solo de envío:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:GetQueueUrl"
      ],
      "Resource": [
        "arn:aws:sqs:us-east-1:ACCOUNT_ID:mscorreos-standard-*",
        "arn:aws:sqs:us-east-1:ACCOUNT_ID:mscorreos-alta-prioridad-*.fifo"
      ]
    }
  ]
}
```

## Troubleshooting

### Problema: "Access Denied" al crear stack

**Solución**: Verificar permisos IAM del usuario/rol.

```bash
# Ver políticas del usuario actual
aws iam list-attached-user-policies --user-name YOUR_USERNAME
```

### Problema: "Stack already exists"

**Solución**: Eliminar el stack existente o usar update en lugar de create.

```bash
# Eliminar stack
./delete-sqs.sh dev

# O actualizar manualmente
aws cloudformation update-stack \
    --stack-name mscorreos-sqs-dev \
    --template-body file://sqs-queues.yaml \
    --parameters ParameterKey=Environment,ParameterValue=dev \
    --region us-east-1
```

### Problema: Mensajes no se procesan

**Solución**: Verificar visibility timeout y que el consumidor esté corriendo.

```bash
# Ver atributos de la cola
aws sqs get-queue-attributes \
    --queue-url $STANDARD_QUEUE_URL \
    --attribute-names All \
    --region us-east-1
```

### Problema: Muchos mensajes en DLQ

**Solución**: Investigar logs de errores y reprocesar mensajes.

```bash
# Ver mensajes en DLQ
aws sqs receive-message \
    --queue-url $DLQ_URL \
    --max-number-of-messages 10 \
    --region us-east-1
```

### Problema: Terraform state lock

**Solución**: Esperar o forzar unlock (con precaución).

```bash
terraform force-unlock <LOCK_ID>
```

## Rollback

### CloudFormation

```bash
# Rollback automático si falla el despliegue
aws cloudformation cancel-update-stack \
    --stack-name mscorreos-sqs-dev \
    --region us-east-1

# O eliminar y recrear
./delete-sqs.sh dev
./deploy-sqs.sh dev
```

### Terraform

```bash
# Volver a versión anterior del state
terraform state pull > backup.tfstate
terraform state push previous.tfstate

# O destruir y recrear
terraform destroy
terraform apply
```

## Checklist de Despliegue

- [ ] AWS CLI instalado y configurado
- [ ] Permisos IAM verificados
- [ ] Template/configuración validada
- [ ] Stack/recursos desplegados exitosamente
- [ ] URLs de colas obtenidas
- [ ] Variables de entorno configuradas en la aplicación
- [ ] Roles de IAM creados y asignados
- [ ] Mensaje de prueba enviado y recibido
- [ ] Alarmas CloudWatch verificadas
- [ ] Documentación actualizada con URLs reales
- [ ] Equipo notificado del despliegue

## Próximos Pasos

Después de desplegar la infraestructura SQS:

1. **Implementar el SQS Consumer** en MSCorreos (Task 2.2)
2. **Configurar Amazon SES** para envío de correos (Task 2.3)
3. **Implementar SNS Listener** para tracking (Task 2.4)
4. **Actualizar productores** en ShrimpSoftServer para publicar eventos

## Soporte

Para problemas o preguntas:
- Revisar logs de CloudFormation/Terraform
- Consultar documentación de AWS SQS
- Contactar al equipo de DevOps/Infraestructura

## Referencias

- [AWS SQS Documentation](https://docs.aws.amazon.com/sqs/)
- [CloudFormation User Guide](https://docs.aws.amazon.com/cloudformation/)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [Spring Cloud AWS](https://docs.awspring.io/spring-cloud-aws/docs/3.0.0/reference/html/)
