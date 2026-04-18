# Configuración de bucket S3 para adjuntos de MSCorreos
# Requirement 13.1, 13.5: Gestión de adjuntos con ciclo de vida de 7 días

variable "environment" {
  description = "Ambiente de despliegue (dev, test, prod)"
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "Región de AWS"
  type        = string
  default     = "us-east-1"
}

variable "aws_account_id" {
  description = "ID de la cuenta de AWS"
  type        = string
}

# Bucket S3 para almacenar adjuntos temporalmente
resource "aws_s3_bucket" "mscorreos_adjuntos" {
  bucket = "mscorreos-adjuntos-${var.environment}"

  tags = {
    Application   = "MSCorreos"
    Environment   = var.environment
    Purpose       = "EmailAttachments"
    DataRetention = "7days"
  }
}

# Habilitar versionamiento
resource "aws_s3_bucket_versioning" "mscorreos_adjuntos_versioning" {
  bucket = aws_s3_bucket.mscorreos_adjuntos.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Cifrado en reposo usando AES-256
resource "aws_s3_bucket_server_side_encryption_configuration" "mscorreos_adjuntos_encryption" {
  bucket = aws_s3_bucket.mscorreos_adjuntos.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Política de ciclo de vida: eliminar archivos después de 7 días (Requirement 13.5)
resource "aws_s3_bucket_lifecycle_configuration" "mscorreos_adjuntos_lifecycle" {
  bucket = aws_s3_bucket.mscorreos_adjuntos.id

  rule {
    id     = "DeleteOldAttachments"
    status = "Enabled"

    expiration {
      days = 7
    }

    noncurrent_version_expiration {
      noncurrent_days = 1
    }
  }
}

# Bloquear acceso público
resource "aws_s3_bucket_public_access_block" "mscorreos_adjuntos_public_access_block" {
  bucket = aws_s3_bucket.mscorreos_adjuntos.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Política de bucket para acceso de MSCorreos (Consumidor) y ShrimpSoftServer (Productor)
resource "aws_s3_bucket_policy" "mscorreos_adjuntos_policy" {
  bucket = aws_s3_bucket.mscorreos_adjuntos.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # Permitir a MSCorreos leer y eliminar adjuntos
      {
        Sid    = "AllowMSCorreosConsumerAccess"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${var.aws_account_id}:role/MSCorreosECSTaskRole-${var.environment}"
        }
        Action = [
          "s3:GetObject",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.mscorreos_adjuntos.arn}/*"
      },
      # Permitir a MSCorreos listar objetos del bucket
      {
        Sid    = "AllowMSCorreosListBucket"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${var.aws_account_id}:role/MSCorreosECSTaskRole-${var.environment}"
        }
        Action = [
          "s3:ListBucket"
        ]
        Resource = aws_s3_bucket.mscorreos_adjuntos.arn
      },
      # Permitir a ShrimpSoftServer (Productor) subir adjuntos
      {
        Sid    = "AllowProducersUpload"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${var.aws_account_id}:role/ShrimpSoftServerRole-${var.environment}"
        }
        Action = [
          "s3:PutObject",
          "s3:PutObjectAcl"
        ]
        Resource = "${aws_s3_bucket.mscorreos_adjuntos.arn}/*"
      },
      # Permitir a ShrimpSoftServer listar objetos (para verificación)
      {
        Sid    = "AllowProducersListBucket"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${var.aws_account_id}:role/ShrimpSoftServerRole-${var.environment}"
        }
        Action = [
          "s3:ListBucket"
        ]
        Resource = aws_s3_bucket.mscorreos_adjuntos.arn
      }
    ]
  })
}

# Alarma CloudWatch para tamaño del bucket
resource "aws_cloudwatch_metric_alarm" "bucket_size_alarm" {
  alarm_name          = "mscorreos-s3-bucket-size-${var.environment}"
  alarm_description   = "Alarma cuando el bucket S3 supera 10 GB"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "BucketSizeBytes"
  namespace           = "AWS/S3"
  period              = 86400 # 1 día
  statistic           = "Average"
  threshold           = 10737418240 # 10 GB en bytes
  treat_missing_data  = "notBreaching"

  dimensions = {
    BucketName  = aws_s3_bucket.mscorreos_adjuntos.id
    StorageType = "StandardStorage"
  }
}

# Alarma CloudWatch para número de objetos
resource "aws_cloudwatch_metric_alarm" "bucket_object_count_alarm" {
  alarm_name          = "mscorreos-s3-object-count-${var.environment}"
  alarm_description   = "Alarma cuando el bucket tiene más de 10,000 objetos"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "NumberOfObjects"
  namespace           = "AWS/S3"
  period              = 86400 # 1 día
  statistic           = "Average"
  threshold           = 10000
  treat_missing_data  = "notBreaching"

  dimensions = {
    BucketName  = aws_s3_bucket.mscorreos_adjuntos.id
    StorageType = "AllStorageTypes"
  }
}

# Outputs
output "bucket_name" {
  description = "Nombre del bucket S3 para adjuntos"
  value       = aws_s3_bucket.mscorreos_adjuntos.id
}

output "bucket_arn" {
  description = "ARN del bucket S3"
  value       = aws_s3_bucket.mscorreos_adjuntos.arn
}

output "bucket_domain_name" {
  description = "Domain name del bucket S3"
  value       = aws_s3_bucket.mscorreos_adjuntos.bucket_domain_name
}

output "bucket_regional_domain_name" {
  description = "Regional domain name del bucket S3"
  value       = aws_s3_bucket.mscorreos_adjuntos.bucket_regional_domain_name
}
