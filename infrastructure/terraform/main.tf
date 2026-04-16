# Configuración de Terraform para colas SQS de MSCorreos
# Alternativa a CloudFormation para equipos que prefieren Terraform

terraform {
  required_version = ">= 1.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Variables
variable "environment" {
  description = "Ambiente de despliegue"
  type        = string
  validation {
    condition     = contains(["dev", "test", "prod"], var.environment)
    error_message = "El ambiente debe ser dev, test o prod"
  }
}

variable "aws_region" {
  description = "Región de AWS"
  type        = string
  default     = "us-east-1"
}

# Dead Letter Queue (DLQ)
resource "aws_sqs_queue" "mscorreos_dlq" {
  name                      = "mscorreos-dlq-${var.environment}"
  message_retention_seconds = 1209600  # 14 días
  visibility_timeout_seconds = 30

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Purpose     = "DeadLetterQueue"
  }
}

# Cola Standard para prioridad MEDIA/BAJA
resource "aws_sqs_queue" "mscorreos_standard" {
  name                       = "mscorreos-standard-${var.environment}"
  message_retention_seconds  = 1209600  # 14 días (Requirement 6.7)
  visibility_timeout_seconds = 30       # 30 segundos (Requirement 2.5)
  receive_wait_time_seconds  = 20       # Long polling de 20 segundos

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.mscorreos_dlq.arn
    maxReceiveCount     = 3  # 3 reintentos (Requirement 2.4, 6.2)
  })

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Priority    = "MEDIA-BAJA"
    QueueType   = "Standard"
  }
}

# Cola FIFO para prioridad ALTA
resource "aws_sqs_queue" "mscorreos_fifo" {
  name                        = "mscorreos-alta-prioridad-${var.environment}.fifo"
  fifo_queue                  = true
  content_based_deduplication = true  # Deduplicación automática
  message_retention_seconds   = 1209600  # 14 días (Requirement 6.7)
  visibility_timeout_seconds  = 30       # 30 segundos (Requirement 2.5)
  receive_wait_time_seconds   = 20       # Long polling de 20 segundos

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.mscorreos_dlq.arn
    maxReceiveCount     = 3  # 3 reintentos (Requirement 2.4, 6.2)
  })

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Priority    = "ALTA"
    QueueType   = "FIFO"
  }
}

# Política de acceso para la cola Standard
resource "aws_sqs_queue_policy" "mscorreos_standard_policy" {
  queue_url = aws_sqs_queue.mscorreos_standard.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = [
            "ec2.amazonaws.com",
            "ecs-tasks.amazonaws.com"
          ]
        }
        Action = [
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.mscorreos_standard.arn
      }
    ]
  })
}

# Política de acceso para la cola FIFO
resource "aws_sqs_queue_policy" "mscorreos_fifo_policy" {
  queue_url = aws_sqs_queue.mscorreos_fifo.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = [
            "ec2.amazonaws.com",
            "ecs-tasks.amazonaws.com"
          ]
        }
        Action = [
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes"
        ]
        Resource = aws_sqs_queue.mscorreos_fifo.arn
      }
    ]
  })
}

# Política de acceso para la DLQ
resource "aws_sqs_queue_policy" "mscorreos_dlq_policy" {
  queue_url = aws_sqs_queue.mscorreos_dlq.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "sqs.amazonaws.com"
        }
        Action   = "sqs:SendMessage"
        Resource = aws_sqs_queue.mscorreos_dlq.arn
      }
    ]
  })
}

# Alarma CloudWatch para DLQ
resource "aws_cloudwatch_metric_alarm" "dlq_alarm" {
  alarm_name          = "mscorreos-dlq-messages-${var.environment}"
  alarm_description   = "Alarma cuando la DLQ contiene más de 10 mensajes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300  # 5 minutos
  statistic           = "Average"
  threshold           = 10
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.mscorreos_dlq.name
  }

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
  }
}

# Alarma CloudWatch para cola Standard
resource "aws_cloudwatch_metric_alarm" "standard_queue_depth_alarm" {
  alarm_name          = "mscorreos-standard-queue-depth-${var.environment}"
  alarm_description   = "Alarma cuando la cola Standard tiene más de 1000 mensajes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300  # 5 minutos
  statistic           = "Average"
  threshold           = 1000
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.mscorreos_standard.name
  }

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
  }
}

# Alarma CloudWatch para cola FIFO
resource "aws_cloudwatch_metric_alarm" "fifo_queue_depth_alarm" {
  alarm_name          = "mscorreos-fifo-queue-depth-${var.environment}"
  alarm_description   = "Alarma cuando la cola FIFO tiene más de 500 mensajes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300  # 5 minutos
  statistic           = "Average"
  threshold           = 500
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = aws_sqs_queue.mscorreos_fifo.name
  }

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
  }
}

# Outputs
output "standard_queue_url" {
  description = "URL de la cola SQS Standard para prioridad MEDIA/BAJA"
  value       = aws_sqs_queue.mscorreos_standard.url
}

output "standard_queue_arn" {
  description = "ARN de la cola SQS Standard"
  value       = aws_sqs_queue.mscorreos_standard.arn
}

output "fifo_queue_url" {
  description = "URL de la cola SQS FIFO para prioridad ALTA"
  value       = aws_sqs_queue.mscorreos_fifo.url
}

output "fifo_queue_arn" {
  description = "ARN de la cola SQS FIFO"
  value       = aws_sqs_queue.mscorreos_fifo.arn
}

output "dlq_url" {
  description = "URL de la Dead Letter Queue"
  value       = aws_sqs_queue.mscorreos_dlq.url
}

output "dlq_arn" {
  description = "ARN de la Dead Letter Queue"
  value       = aws_sqs_queue.mscorreos_dlq.arn
}
