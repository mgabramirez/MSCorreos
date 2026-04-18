#!/bin/bash

# Script para desplegar Amazon SNS Topic para tracking de eventos SES - MSCorreos
# Uso: ./deploy-sns.sh [dev|test|prod] [mscorreos-endpoint-url]

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

print_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
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

print_info "=========================================="
print_info "DESPLIEGUE DE AMAZON SNS - MSCORREOS"
print_info "=========================================="
print_info "Ambiente: $ENVIRONMENT"
print_info "Región AWS: $AWS_REGION"
print_info ""

# Cambiar al directorio de CloudFormation
SCRIPT_DIR="$(dirname "$0")"
cd "$SCRIPT_DIR/../cloudformation"

# Validar que el template existe
if [ ! -f "sns-topic.yaml" ]; then
    print_error "Template sns-topic.yaml no encontrado"
    exit 1
fi

# Configurar endpoint de MSCorreos
if [ -z "$MSCORREOS_ENDPOINT" ]; then
    print_warning "No se proporcionó endpoint de MSCorreos."
    print_warning "Usando endpoint por defecto basado en ambiente."
    MSCORREOS_ENDPOINT="https://mscorreos-${ENVIRONMENT}.acosux.com/sns/notifications"
    print_info "Endpoint: $MSCORREOS_ENDPOINT"
    print_warning "Si este endpoint no es correcto, cancele (Ctrl+C) y proporcione el endpoint correcto."
    sleep 3
fi

SNS_STACK_NAME="mscorreos-sns-tracking-${ENVIRONMENT}"

# Paso 1: Validar template
print_step "Paso 1/3: Validando template de CloudFormation..."
aws cloudformation validate-template \
    --template-body file://sns-topic.yaml \
    --region $AWS_REGION > /dev/null

print_info "✓ Template válido"

# Paso 2: Desplegar o actualizar stack
print_step "Paso 2/3: Desplegando SNS Topic..."

# Verificar si el stack ya existe
if aws cloudformation describe-stacks --stack-name $SNS_STACK_NAME --region $AWS_REGION > /dev/null 2>&1; then
    print_info "Stack existente detectado. Actualizando..."
    
    UPDATE_OUTPUT=$(aws cloudformation update-stack \
        --stack-name $SNS_STACK_NAME \
        --template-body file://sns-topic.yaml \
        --parameters \
            ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
            ParameterKey=MSCorreosEndpoint,ParameterValue=$MSCORREOS_ENDPOINT \
        --region $AWS_REGION \
        --capabilities CAPABILITY_IAM 2>&1) || {
            EXIT_CODE=$?
            if echo "$UPDATE_OUTPUT" | grep -q "No updates are to be performed"; then
                print_warning "No hay cambios para actualizar en el stack"
            else
                print_error "Error actualizando stack: $UPDATE_OUTPUT"
                exit $EXIT_CODE
            fi
        }
    
    if [ $? -eq 0 ]; then
        print_info "Esperando a que se complete la actualización..."
        aws cloudformation wait stack-update-complete \
            --stack-name $SNS_STACK_NAME \
            --region $AWS_REGION 2>/dev/null || true
        print_info "✓ Stack actualizado exitosamente"
    fi
else
    print_info "Creando nuevo stack..."
    
    aws cloudformation create-stack \
        --stack-name $SNS_STACK_NAME \
        --template-body file://sns-topic.yaml \
        --parameters \
            ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
            ParameterKey=MSCorreosEndpoint,ParameterValue=$MSCORREOS_ENDPOINT \
        --region $AWS_REGION \
        --capabilities CAPABILITY_IAM
    
    print_info "Esperando a que se complete la creación..."
    aws cloudformation wait stack-create-complete \
        --stack-name $SNS_STACK_NAME \
        --region $AWS_REGION
    
    print_info "✓ Stack creado exitosamente"
fi

# Paso 3: Obtener outputs del stack
print_step "Paso 3/3: Obteniendo información del despliegue..."

SNS_TOPIC_ARN=$(aws cloudformation describe-stacks \
    --stack-name $SNS_STACK_NAME \
    --region $AWS_REGION \
    --query 'Stacks[0].Outputs[?OutputKey==`TopicArn`].OutputValue' \
    --output text)

SNS_TOPIC_NAME=$(aws cloudformation describe-stacks \
    --stack-name $SNS_STACK_NAME \
    --region $AWS_REGION \
    --query 'Stacks[0].Outputs[?OutputKey==`TopicName`].OutputValue' \
    --output text)

SUBSCRIPTION_ARN=$(aws cloudformation describe-stacks \
    --stack-name $SNS_STACK_NAME \
    --region $AWS_REGION \
    --query 'Stacks[0].Outputs[?OutputKey==`SubscriptionArn`].OutputValue' \
    --output text)

# Verificar estado de la suscripción
print_info "Verificando estado de la suscripción..."
SUBSCRIPTION_STATUS=$(aws sns get-subscription-attributes \
    --subscription-arn "$SUBSCRIPTION_ARN" \
    --region $AWS_REGION \
    --query 'Attributes.PendingConfirmation' \
    --output text 2>/dev/null || echo "true")

# Resumen final
print_info ""
print_info "=========================================="
print_info "✓ DESPLIEGUE COMPLETADO EXITOSAMENTE"
print_info "=========================================="
print_info ""
print_info "Recursos creados:"
print_info "  Stack Name:       $SNS_STACK_NAME"
print_info "  Topic Name:       $SNS_TOPIC_NAME"
print_info "  Topic ARN:        $SNS_TOPIC_ARN"
print_info "  Subscription ARN: $SUBSCRIPTION_ARN"
print_info "  Endpoint:         $MSCORREOS_ENDPOINT"
print_info ""

if [ "$SUBSCRIPTION_STATUS" = "true" ]; then
    print_warning "⚠️  ACCIÓN REQUERIDA: CONFIRMAR SUSCRIPCIÓN SNS"
    print_warning ""
    print_warning "La suscripción HTTPS está en estado 'PendingConfirmation'"
    print_warning ""
    print_warning "Para confirmar la suscripción:"
    print_warning "  1. SNS enviará un mensaje POST al endpoint: $MSCORREOS_ENDPOINT"
    print_warning "  2. El mensaje contendrá un campo 'SubscribeURL'"
    print_warning "  3. MSCorreos debe hacer una petición GET a ese URL para confirmar"
    print_warning "  4. Alternativamente, puede confirmar manualmente desde la consola AWS"
    print_warning ""
    print_warning "Verificar logs de MSCorreos para ver el mensaje de confirmación"
else
    print_info "✓ Suscripción confirmada y activa"
fi

print_info ""
print_info "Configuración de SES:"
print_info "  - Este topic debe configurarse como Event Destination en SES Configuration Set"
print_info "  - Eventos soportados: Send, Delivery, Open, Bounce, Complaint, Reject, RenderingFailure"
print_info ""
print_info "Alarmas configuradas:"
print_info "  - mscorreos-sns-failed-notifications-${ENVIRONMENT}"
print_info "    Umbral: > 5 mensajes fallidos en 5 minutos"
print_info ""
print_info "Próximos pasos:"
print_info "  1. ✓ SNS Topic creado"
print_info "  2. ⏳ Confirmar suscripción desde endpoint MSCorreos"
print_info "  3. ⏳ Configurar SES Configuration Set para usar este topic"
print_info "  4. ⏳ Implementar SNS Listener en MSCorreos (Task 2.4)"
print_info "  5. ⏳ Probar envío de correo y verificar eventos"
print_info ""
print_info "Comandos útiles:"
print_info "  # Ver detalles del stack"
print_info "  aws cloudformation describe-stacks --stack-name $SNS_STACK_NAME --region $AWS_REGION"
print_info ""
print_info "  # Ver estado de la suscripción"
print_info "  aws sns get-subscription-attributes --subscription-arn $SUBSCRIPTION_ARN --region $AWS_REGION"
print_info ""
print_info "  # Listar suscripciones del topic"
print_info "  aws sns list-subscriptions-by-topic --topic-arn $SNS_TOPIC_ARN --region $AWS_REGION"
print_info ""
print_info "  # Publicar mensaje de prueba"
print_info "  aws sns publish --topic-arn $SNS_TOPIC_ARN --message 'Test message' --region $AWS_REGION"
print_info ""

# Guardar outputs en archivo
OUTPUT_FILE="$SCRIPT_DIR/../outputs/sns-${ENVIRONMENT}.txt"
mkdir -p "$SCRIPT_DIR/../outputs"

cat > "$OUTPUT_FILE" << EOF
# SNS Topic Outputs - Ambiente: $ENVIRONMENT
# Generado: $(date)

SNS_TOPIC_ARN=$SNS_TOPIC_ARN
SNS_TOPIC_NAME=$SNS_TOPIC_NAME
SUBSCRIPTION_ARN=$SUBSCRIPTION_ARN
MSCORREOS_ENDPOINT=$MSCORREOS_ENDPOINT
SUBSCRIPTION_STATUS=$SUBSCRIPTION_STATUS
AWS_REGION=$AWS_REGION

# Para usar en variables de entorno:
export SNS_TOPIC_ARN="$SNS_TOPIC_ARN"
export SNS_TOPIC_NAME="$SNS_TOPIC_NAME"
export MSCORREOS_ENDPOINT="$MSCORREOS_ENDPOINT"
EOF

print_info "Outputs guardados en: $OUTPUT_FILE"
print_info ""

