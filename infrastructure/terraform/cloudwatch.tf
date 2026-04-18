# Configuración de CloudWatch para MSCorreos
# Logs, Métricas y Alarmas

# ============================================================================
# LOG GROUPS - Retención de 30 días (Requirement 8.7)
# ============================================================================

resource "aws_cloudwatch_log_group" "mscorreos_main" {
  name              = "/aws/mscorreos/${var.environment}"
  retention_in_days = 30

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Purpose     = "ApplicationLogs"
  }
}

resource "aws_cloudwatch_log_group" "sqs_consumer" {
  name              = "/aws/mscorreos/${var.environment}/sqs-consumer"
  retention_in_days = 30

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Component   = "SQSConsumer"
  }
}

resource "aws_cloudwatch_log_group" "email_service" {
  name              = "/aws/mscorreos/${var.environment}/email-service"
  retention_in_days = 30

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Component   = "EmailService"
  }
}

resource "aws_cloudwatch_log_group" "sns_listener" {
  name              = "/aws/mscorreos/${var.environment}/sns-listener"
  retention_in_days = 30

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Component   = "SNSListener"
  }
}

resource "aws_cloudwatch_log_group" "blacklist_service" {
  name              = "/aws/mscorreos/${var.environment}/blacklist-service"
  retention_in_days = 30

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Component   = "BlacklistService"
  }
}

# ============================================================================
# METRIC FILTERS - Para crear métricas personalizadas desde logs
# ============================================================================

resource "aws_cloudwatch_log_metric_filter" "error_count" {
  name           = "mscorreos-error-count-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.mscorreos_main.name
  pattern        = "[timestamp, level=ERROR*, ...]"

  metric_transformation {
    name          = "ErrorCount"
    namespace     = "MSCorreos"
    value         = "1"
    default_value = 0
    unit          = "Count"
    dimensions = {
      Environment = var.environment
    }
  }
}

resource "aws_cloudwatch_log_metric_filter" "emails_sent" {
  name           = "mscorreos-emails-sent-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.email_service.name
  pattern        = "[timestamp, level, message=\"Email sent successfully\"*, ...]"

  metric_transformation {
    name          = "CorreosEnviados"
    namespace     = "MSCorreos"
    value         = "1"
    default_value = 0
    unit          = "Count"
    dimensions = {
      Environment = var.environment
    }
  }
}

resource "aws_cloudwatch_log_metric_filter" "emails_failed" {
  name           = "mscorreos-emails-failed-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.email_service.name
  pattern        = "[timestamp, level=ERROR, message=\"Failed to send email\"*, ...]"

  metric_transformation {
    name          = "CorreosFallidos"
    namespace     = "MSCorreos"
    value         = "1"
    default_value = 0
    unit          = "Count"
    dimensions = {
      Environment = var.environment
    }
  }
}

resource "aws_cloudwatch_log_metric_filter" "emails_blocked" {
  name           = "mscorreos-emails-blocked-${var.environment}"
  log_group_name = aws_cloudwatch_log_group.blacklist_service.name
  pattern        = "[timestamp, level, message=\"Email blocked by blacklist\"*, ...]"

  metric_transformation {
    name          = "CorreosBloqueados"
    namespace     = "MSCorreos"
    value         = "1"
    default_value = 0
    unit          = "Count"
    dimensions = {
      Environment = var.environment
    }
  }
}

# ============================================================================
# SNS TOPIC - Para notificaciones de alarmas
# ============================================================================

resource "aws_sns_topic" "alarm_notifications" {
  name         = "mscorreos-alarms-${var.environment}"
  display_name = "MSCorreos Alarmas - ${var.environment}"

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
  }
}

# Suscripción por email (opcional)
variable "alarm_email" {
  description = "Email para notificaciones de alarmas (opcional)"
  type        = string
  default     = ""
}

resource "aws_sns_topic_subscription" "alarm_email" {
  count     = var.alarm_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alarm_notifications.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

# ============================================================================
# ALARMAS - CloudWatch Alarms (Requirements 8.3, 8.4, 8.5, 16.15)
# ============================================================================

# Alarma: Tasa de errores > 5% en 5 minutos (Requirement 8.4)
resource "aws_cloudwatch_metric_alarm" "error_rate" {
  alarm_name          = "mscorreos-error-rate-${var.environment}"
  alarm_description   = "Alarma cuando la tasa de errores supera el 5% en 5 minutos"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ErrorCount"
  namespace           = "MSCorreos"
  period              = 300 # 5 minutos
  statistic           = "Sum"
  threshold           = 5
  treat_missing_data  = "notBreaching"

  dimensions = {
    Environment = var.environment
  }

  alarm_actions = [aws_sns_topic.alarm_notifications.arn]

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Severity    = "High"
  }
}

# Alarma: Tiempo de procesamiento promedio > 5 segundos (Requirement 8.5)
resource "aws_cloudwatch_metric_alarm" "processing_time" {
  alarm_name          = "mscorreos-processing-time-${var.environment}"
  alarm_description   = "Alarma cuando el tiempo de procesamiento promedio supera 5 segundos"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "TiempoProcesamiento"
  namespace           = "MSCorreos"
  period              = 300 # 5 minutos
  statistic           = "Average"
  threshold           = 5000 # 5 segundos en milisegundos
  treat_missing_data  = "notBreaching"
  unit                = "Milliseconds"

  dimensions = {
    Environment = var.environment
  }

  alarm_actions = [aws_sns_topic.alarm_notifications.arn]

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Severity    = "Medium"
  }
}

# Alarma: Lista negra > 1000 correos (Requirement 16.15)
resource "aws_cloudwatch_metric_alarm" "blacklist_size" {
  alarm_name          = "mscorreos-blacklist-size-${var.environment}"
  alarm_description   = "Alarma cuando la lista negra supera 1000 correos electrónicos"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "TotalCorreosListaNegra"
  namespace           = "MSCorreos"
  period              = 3600 # 1 hora
  statistic           = "Maximum"
  threshold           = 1000
  treat_missing_data  = "notBreaching"

  dimensions = {
    Environment = var.environment
  }

  alarm_actions = [aws_sns_topic.alarm_notifications.arn]

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Severity    = "High"
  }
}

# Alarma: DLQ con más de 10 mensajes (Requirement 8.3)
resource "aws_cloudwatch_metric_alarm" "dlq_messages" {
  alarm_name          = "mscorreos-dlq-high-messages-${var.environment}"
  alarm_description   = "Alarma cuando la DLQ contiene más de 10 mensajes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300 # 5 minutos
  statistic           = "Average"
  threshold           = 10
  treat_missing_data  = "notBreaching"

  dimensions = {
    QueueName = "mscorreos-dlq-${var.environment}"
  }

  alarm_actions = [aws_sns_topic.alarm_notifications.arn]

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Severity    = "Critical"
  }
}

# Alarma: Correos fallidos consecutivos
resource "aws_cloudwatch_metric_alarm" "consecutive_failures" {
  alarm_name          = "mscorreos-consecutive-failures-${var.environment}"
  alarm_description   = "Alarma cuando hay más de 10 correos fallidos consecutivos"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CorreosFallidos"
  namespace           = "MSCorreos"
  period              = 60 # 1 minuto
  statistic           = "Sum"
  threshold           = 10
  treat_missing_data  = "notBreaching"

  dimensions = {
    Environment = var.environment
  }

  alarm_actions = [aws_sns_topic.alarm_notifications.arn]

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Severity    = "High"
  }
}

# Alarma: Correos bloqueados por lista negra (alta tasa)
resource "aws_cloudwatch_metric_alarm" "high_blocked_emails" {
  alarm_name          = "mscorreos-high-blocked-emails-${var.environment}"
  alarm_description   = "Alarma cuando se bloquean más de 50 correos en 5 minutos"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "CorreosBloqueados"
  namespace           = "MSCorreos"
  period              = 300 # 5 minutos
  statistic           = "Sum"
  threshold           = 50
  treat_missing_data  = "notBreaching"

  dimensions = {
    Environment = var.environment
  }

  alarm_actions = [aws_sns_topic.alarm_notifications.arn]

  tags = {
    Application = "MSCorreos"
    Environment = var.environment
    Severity    = "Medium"
  }
}

# ============================================================================
# DASHBOARD - CloudWatch Dashboard para visualización
# ============================================================================

resource "aws_cloudwatch_dashboard" "mscorreos" {
  dashboard_name = "MSCorreos-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          metrics = [
            ["MSCorreos", "CorreosEnviados", { stat = "Sum", label = "Correos Enviados" }],
            [".", "CorreosFallidos", { stat = "Sum", label = "Correos Fallidos" }],
            [".", "CorreosBloqueados", { stat = "Sum", label = "Correos Bloqueados" }]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Correos - Resumen"
          period  = 300
          yAxis = {
            left = {
              label = "Count"
            }
          }
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["MSCorreos", "TiempoProcesamiento", { stat = "Average", label = "Promedio" }],
            ["...", { stat = "Maximum", label = "Máximo" }]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Tiempo de Procesamiento (ms)"
          period  = 300
          yAxis = {
            left = {
              label = "Milliseconds"
            }
          }
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", { stat = "Average", label = "Standard Queue" }, { QueueName = "mscorreos-standard-${var.environment}" }],
            ["...", { QueueName = "mscorreos-alta-prioridad-${var.environment}.fifo", label = "FIFO Queue" }],
            ["...", { QueueName = "mscorreos-dlq-${var.environment}", label = "DLQ" }]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Mensajes en Colas SQS"
          period  = 300
          yAxis = {
            left = {
              label = "Messages"
            }
          }
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["MSCorreos", "ErrorCount", { stat = "Sum", label = "Errores" }]
          ]
          view    = "timeSeries"
          stacked = false
          region  = var.aws_region
          title   = "Errores de Aplicación"
          period  = 300
          yAxis = {
            left = {
              label = "Count"
            }
          }
        }
      },
      {
        type = "metric"
        properties = {
          metrics = [
            ["MSCorreos", "TotalCorreosListaNegra", { stat = "Maximum", label = "Total en Lista Negra" }]
          ]
          view   = "singleValue"
          region = var.aws_region
          title  = "Lista Negra - Total Correos"
        }
      },
      {
        type = "log"
        properties = {
          query  = "SOURCE '/aws/mscorreos/${var.environment}'\n| fields @timestamp, level, message\n| filter level = 'ERROR'\n| sort @timestamp desc\n| limit 20"
          region = var.aws_region
          title  = "Últimos Errores"
        }
      }
    ]
  })
}

# ============================================================================
# OUTPUTS
# ============================================================================

output "log_group_name" {
  description = "Nombre del Log Group principal de MSCorreos"
  value       = aws_cloudwatch_log_group.mscorreos_main.name
}

output "log_group_arn" {
  description = "ARN del Log Group principal de MSCorreos"
  value       = aws_cloudwatch_log_group.mscorreos_main.arn
}

output "alarm_topic_arn" {
  description = "ARN del SNS Topic para notificaciones de alarmas"
  value       = aws_sns_topic.alarm_notifications.arn
}

output "dashboard_url" {
  description = "URL del Dashboard de CloudWatch"
  value       = "https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}#dashboards:name=MSCorreos-${var.environment}"
}

output "alarm_names" {
  description = "Nombres de las alarmas creadas"
  value = {
    error_rate           = aws_cloudwatch_metric_alarm.error_rate.alarm_name
    processing_time      = aws_cloudwatch_metric_alarm.processing_time.alarm_name
    blacklist_size       = aws_cloudwatch_metric_alarm.blacklist_size.alarm_name
    dlq_messages         = aws_cloudwatch_metric_alarm.dlq_messages.alarm_name
    consecutive_failures = aws_cloudwatch_metric_alarm.consecutive_failures.alarm_name
    high_blocked_emails  = aws_cloudwatch_metric_alarm.high_blocked_emails.alarm_name
  }
}
