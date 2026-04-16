# Terraform Configuration for MSCorreos SQS

Esta es una alternativa a CloudFormation para equipos que prefieren usar Terraform.

## Requisitos Previos

1. **Terraform** instalado (versión >= 1.0)
   ```bash
   terraform --version
   ```

2. **AWS CLI** configurado
   ```bash
   aws configure
   ```

3. **Permisos IAM** necesarios (mismos que CloudFormation)

## Uso

### 1. Inicializar Terraform

```bash
cd infrastructure/terraform
terraform init
```

### 2. Crear archivo de variables

```bash
cp terraform.tfvars.example terraform.tfvars
```

Editar `terraform.tfvars`:
```hcl
environment = "dev"  # o "test" o "prod"
aws_region  = "us-east-1"
```

### 3. Planificar cambios

```bash
terraform plan
```

### 4. Aplicar configuración

```bash
terraform apply
```

Confirmar con `yes` cuando se solicite.

### 5. Ver outputs

```bash
terraform output
```

Para obtener un valor específico:
```bash
terraform output standard_queue_url
terraform output fifo_queue_url
terraform output dlq_url
```

## Comandos Útiles

### Ver estado actual
```bash
terraform show
```

### Listar recursos
```bash
terraform state list
```

### Destruir infraestructura
```bash
terraform destroy
```

**⚠️ ADVERTENCIA**: Esto eliminará todas las colas y sus mensajes.

### Formatear código
```bash
terraform fmt
```

### Validar configuración
```bash
terraform validate
```

## Estructura de Archivos

```
terraform/
├── main.tf                      # Configuración principal
├── terraform.tfvars.example     # Ejemplo de variables
├── terraform.tfvars             # Variables (no versionado)
└── README.md                    # Este archivo
```

## Variables

| Variable | Descripción | Valores Permitidos | Default |
|----------|-------------|-------------------|---------|
| `environment` | Ambiente de despliegue | dev, test, prod | - |
| `aws_region` | Región de AWS | Cualquier región válida | us-east-1 |

## Outputs

| Output | Descripción |
|--------|-------------|
| `standard_queue_url` | URL de la cola Standard |
| `standard_queue_arn` | ARN de la cola Standard |
| `fifo_queue_url` | URL de la cola FIFO |
| `fifo_queue_arn` | ARN de la cola FIFO |
| `dlq_url` | URL de la DLQ |
| `dlq_arn` | ARN de la DLQ |

## Backend Remoto (Opcional)

Para equipos, se recomienda usar un backend remoto en S3:

```hcl
# Agregar al inicio de main.tf
terraform {
  backend "s3" {
    bucket         = "mi-empresa-terraform-state"
    key            = "mscorreos/sqs/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}
```

## Troubleshooting

### Error: "No valid credential sources found"

Configurar AWS CLI:
```bash
aws configure
```

### Error: "Error acquiring the state lock"

Otro usuario está aplicando cambios. Esperar o forzar unlock (con cuidado):
```bash
terraform force-unlock <LOCK_ID>
```

## Comparación con CloudFormation

| Aspecto | CloudFormation | Terraform |
|---------|---------------|-----------|
| Proveedor | AWS nativo | Multi-cloud |
| Sintaxis | YAML/JSON | HCL |
| Estado | Gestionado por AWS | Archivo local/remoto |
| Rollback | Automático | Manual |
| Modularidad | Nested stacks | Modules |

Ambas opciones son válidas. Elegir según preferencia del equipo.
