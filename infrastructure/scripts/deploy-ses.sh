#!/bin/bash

# Script para desplegar la configuración de Amazon SES para MSCorreos
# Uso: ./deploy-ses.sh [dev|test|prod] [mscorreos-endpoint-url]

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Función para imprimir mensajes
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Validar argumentos
if [ $# -lt 1 ]; then
    print_error "Uso: $0 [dev|test|prod] [mscorreos-endpoint-url]"
    print_info "Ejemplo: $0 dev https://mscorreos-dev.acosux.com/sns/notifications"
    exit 1
fi

ENVIRONMENT=$1
MSCORREOS_ENDPOINT=${2:-""}
AWS_REGION="us-east-1"

# Validar ambiente
if [[ ! "$ENVIRONMENT" =~ ^(dev|test|prod)$ ]]; then
    print_error "Ambiente inválido. Debe ser: dev, test o prod"
    exit 1
fi

print_info "Iniciando despliegue de Amazon SES para ambiente: $ENVIRONMENT"
print_info "Región AWS: $AWS_REGION"

# Cambiar al directorio de CloudFormation
cd "$(dirname "$0")/../cloudformation"

# Paso 1: Desplegar SNS Topic
print_info "Paso 1/4: Desplegando SNS Topic para tracking de eventos..."

SNS_STACK_NAME="mscorreos-sns-tracking-${ENVIRONMENT}"

# Validar template SNS
print_info "Validando template SNS..."
aws cloudformation validate-template \
    --template-body file://sns-topic.yaml \
    --region $AWS_REGION > /dev/null

if [ -z "$MSCORREOS_ENDPOINT" ]; then
    print_warning "No se proporcionó endpoint de MSCorreos. Usando placeholder."
    print_warning "Deberá actualizar la suscripción SNS manualmente después."
    MSCORREOS_ENDPOINT="https://mscorreos-${ENVIRONMENT}.acosux.com/sns/notifications"
fi

# Verificar si el stack SNS ya existe
if aws cloudformation describe-stacks --stack-name $SNS_STACK_NAME --region $AWS_REGION > /dev/null 2>&1; then
    print_info "Stack SNS existe. Actualizando..."
    
    aws cloudformation update-stack \
        --stack-name $SNS_STACK_NAME \
        --template-body file://sns-topic.yaml \
        --parameters \
            ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
            ParameterKey=MSCorreosEndpoint,ParameterValue=$MSCORREOS_ENDPOINT \
        --region $AWS_REGION \
        --capabilities CAPABILITY_IAM || {
            if [ $? -eq 254 ]; then
                print_warning "No hay cambios para actualizar en el stack SNS"
            else
                print_error "Error actualizando stack SNS"
                exit 1
            fi
        }
    
    print_info "Esperando a que se complete la actualización del stack SNS..."
    aws cloudformation wait stack-update-complete \
        --stack-name $SNS_STACK_NAME \
        --region $AWS_REGION 2>/dev/null || true
else
    print_info "Creando nuevo stack SNS..."
    
    aws cloudformation create-stack \
        --stack-name $SNS_STACK_NAME \
        --template-body file://sns-topic.yaml \
        --parameters \
            ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
            ParameterKey=MSCorreosEndpoint,ParameterValue=$MSCORREOS_ENDPOINT \
        --region $AWS_REGION \
        --capabilities CAPABILITY_IAM
    
    print_info "Esperando a que se complete la creación del stack SNS..."
    aws cloudformation wait stack-create-complete \
        --stack-name $SNS_STACK_NAME \
        --region $AWS_REGION
fi

print_info "Stack SNS desplegado exitosamente"

# Obtener ARN del topic SNS
SNS_TOPIC_ARN=$(aws cloudformation describe-stacks \
    --stack-name $SNS_STACK_NAME \
    --region $AWS_REGION \
    --query 'Stacks[0].Outputs[?OutputKey==`TopicArn`].OutputValue' \
    --output text)

print_info "SNS Topic ARN: $SNS_TOPIC_ARN"

# Paso 2: Desplegar SES Configuration Set
print_info "Paso 2/4: Desplegando SES Configuration Set..."

SES_STACK_NAME="mscorreos-ses-config-${ENVIRONMENT}"

# Validar template SES
print_info "Validando template SES..."
aws cloudformation validate-template \
    --template-body file://ses-configuration.yaml \
    --region $AWS_REGION > /dev/null

# Verificar si el stack SES ya existe
if aws cloudformation describe-stacks --stack-name $SES_STACK_NAME --region $AWS_REGION > /dev/null 2>&1; then
    print_info "Stack SES existe. Actualizando..."
    
    aws cloudformation update-stack \
        --stack-name $SES_STACK_NAME \
        --template-body file://ses-configuration.yaml \
        --parameters \
            ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
            ParameterKey=SNSTopicArn,ParameterValue=$SNS_TOPIC_ARN \
        --region $AWS_REGION \
        --capabilities CAPABILITY_IAM || {
            if [ $? -eq 254 ]; then
                print_warning "No hay cambios para actualizar en el stack SES"
            else
                print_error "Error actualizando stack SES"
                exit 1
            fi
        }
    
    print_info "Esperando a que se complete la actualización del stack SES..."
    aws cloudformation wait stack-update-complete \
        --stack-name $SES_STACK_NAME \
        --region $AWS_REGION 2>/dev/null || true
else
    print_info "Creando nuevo stack SES..."
    
    aws cloudformation create-stack \
        --stack-name $SES_STACK_NAME \
        --template-body file://ses-configuration.yaml \
        --parameters \
            ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
            ParameterKey=SNSTopicArn,ParameterValue=$SNS_TOPIC_ARN \
        --region $AWS_REGION \
        --capabilities CAPABILITY_IAM
    
    print_info "Esperando a que se complete la creación del stack SES..."
    aws cloudformation wait stack-create-complete \
        --stack-name $SES_STACK_NAME \
        --region $AWS_REGION
fi

print_info "Stack SES desplegado exitosamente"

# Obtener nombre del Configuration Set
CONFIG_SET_NAME=$(aws cloudformation describe-stacks \
    --stack-name $SES_STACK_NAME \
    --region $AWS_REGION \
    --query 'Stacks[0].Outputs[?OutputKey==`ConfigurationSetName`].OutputValue' \
    --output text)

print_info "Configuration Set: $CONFIG_SET_NAME"

# Paso 3: Verificar identidades (dominio y email)
print_info "Paso 3/4: Verificando identidades de SES..."

VERIFIED_DOMAIN="documentos-electronicos.info"
FROM_EMAIL="notificaciones@documentos-electronicos.info"

# Verificar dominio
print_info "Verificando dominio: $VERIFIED_DOMAIN"
aws ses verify-domain-identity \
    --domain $VERIFIED_DOMAIN \
    --region $AWS_REGION > /dev/null 2>&1 || {
        print_warning "El dominio ya está verificado o en proceso de verificación"
    }

# Obtener tokens de verificación DNS
print_info "Obteniendo tokens de verificación DNS..."
VERIFICATION_TOKEN=$(aws ses verify-domain-identity \
    --domain $VERIFIED_DOMAIN \
    --region $AWS_REGION \
    --query 'VerificationToken' \
    --output text 2>/dev/null || echo "N/A")

if [ "$VERIFICATION_TOKEN" != "N/A" ]; then
    print_warning "IMPORTANTE: Debe agregar el siguiente registro TXT a su DNS:"
    print_warning "Nombre: _amazonses.$VERIFIED_DOMAIN"
    print_warning "Valor: $VERIFICATION_TOKEN"
    print_warning "Tipo: TXT"
fi

# Verificar email individual (útil para testing en sandbox)
print_info "Verificando email: $FROM_EMAIL"
aws ses verify-email-identity \
    --email-address $FROM_EMAIL \
    --region $AWS_REGION > /dev/null 2>&1 || {
        print_warning "El email ya está verificado o en proceso de verificación"
    }

print_warning "IMPORTANTE: Revise el buzón $FROM_EMAIL para confirmar la verificación"

# Paso 4: Configurar límites de envío
print_info "Paso 4/4: Verificando límites de envío de SES..."

# Obtener límites actuales
SEND_QUOTA=$(aws ses get-send-quota --region $AWS_REGION)
MAX_SEND_RATE=$(echo $SEND_QUOTA | jq -r '.MaxSendRate')
MAX_24_HOUR=$(echo $SEND_QUOTA | jq -r '.Max24HourSend')

print_info "Límites actuales de SES:"
print_info "  - Tasa máxima de envío: $MAX_SEND_RATE correos/segundo"
print_info "  - Máximo en 24 horas: $MAX_24_HOUR correos"

if (( $(echo "$MAX_SEND_RATE < 14" | bc -l) )); then
    print_warning "La tasa de envío actual ($MAX_SEND_RATE/s) es menor a 14 correos/segundo"
    print_warning "Para aumentar el límite, debe solicitar un aumento a AWS Support:"
    print_warning "https://console.aws.amazon.com/support/home#/case/create?issueType=service-limit-increase"
else
    print_info "La tasa de envío cumple con el requisito de 14 correos/segundo"
fi

# Verificar si está en sandbox
ACCOUNT_STATUS=$(aws sesv2 get-account --region $AWS_REGION --query 'ProductionAccess' --output text 2>/dev/null || echo "false")

if [ "$ACCOUNT_STATUS" = "false" ]; then
    print_warning "⚠️  SU CUENTA SES ESTÁ EN MODO SANDBOX ⚠️"
    print_warning "En modo sandbox solo puede enviar correos a direcciones verificadas"
    print_warning "Para salir del sandbox, solicite acceso de producción:"
    print_warning "https://console.aws.amazon.com/ses/home#/account"
else
    print_info "✓ Su cuenta SES tiene acceso de producción"
fi

# Resumen final
print_info ""
print_info "=========================================="
print_info "DESPLIEGUE COMPLETADO EXITOSAMENTE"
print_info "=========================================="
print_info ""
print_info "Recursos creados:"
print_info "  - SNS Topic: $SNS_TOPIC_ARN"
print_info "  - Configuration Set: $CONFIG_SET_NAME"
print_info "  - Dominio verificado: $VERIFIED_DOMAIN"
print_info "  - Email verificado: $FROM_EMAIL"
print_info ""
print_info "Próximos pasos:"
print_info "  1. Agregar registro TXT a DNS para verificar dominio (si aplica)"
print_info "  2. Confirmar verificación de email desde el buzón"
print_info "  3. Confirmar suscripción SNS desde el endpoint de MSCorreos"
print_info "  4. Solicitar salida de sandbox si es necesario"
print_info "  5. Solicitar aumento de límites si es necesario"
print_info ""
print_info "Para ver los outputs completos:"
print_info "  aws cloudformation describe-stacks --stack-name $SNS_STACK_NAME --region $AWS_REGION"
print_info "  aws cloudformation describe-stacks --stack-name $SES_STACK_NAME --region $AWS_REGION"
print_info ""
