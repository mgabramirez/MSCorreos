#!/bin/bash

# Script para eliminar la configuración de Amazon SNS para MSCorreos
# Uso: ./delete-sns.sh [dev|test|prod]

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
    print_error "Uso: $0 [dev|test|prod]"
    print_info "Ejemplo: $0 dev"
    exit 1
fi

ENVIRONMENT=$1
AWS_REGION="us-east-1"

# Validar ambiente
if [[ ! "$ENVIRONMENT" =~ ^(dev|test|prod)$ ]]; then
    print_error "Ambiente inválido. Debe ser: dev, test o prod"
    exit 1
fi

SNS_STACK_NAME="mscorreos-sns-tracking-${ENVIRONMENT}"

print_warning "=========================================="
print_warning "⚠️  ADVERTENCIA: ELIMINACIÓN DE SNS TOPIC"
print_warning "=========================================="
print_warning "Está a punto de eliminar el SNS Topic para ambiente: $ENVIRONMENT"
print_warning "Stack: $SNS_STACK_NAME"
print_warning ""
print_warning "Esto eliminará:"
print_warning "  - SNS Topic para tracking de eventos SES"
print_warning "  - Suscripción HTTPS al endpoint de MSCorreos"
print_warning "  - Política de acceso para SES"
print_warning "  - Alarma de CloudWatch"
print_warning ""
print_warning "NOTA: Si SES Configuration Set está usando este topic,"
print_warning "      los eventos de tracking dejarán de funcionar."
print_warning ""
read -p "¿Está seguro que desea continuar? (escriba 'yes' para confirmar): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    print_info "Operación cancelada"
    exit 0
fi

# Verificar si el stack existe
if ! aws cloudformation describe-stacks --stack-name $SNS_STACK_NAME --region $AWS_REGION > /dev/null 2>&1; then
    print_error "Stack $SNS_STACK_NAME no existe en la región $AWS_REGION"
    exit 1
fi

print_info "Eliminando stack SNS: $SNS_STACK_NAME"

# Eliminar stack
aws cloudformation delete-stack \
    --stack-name $SNS_STACK_NAME \
    --region $AWS_REGION

print_info "Esperando a que se complete la eliminación..."
aws cloudformation wait stack-delete-complete \
    --stack-name $SNS_STACK_NAME \
    --region $AWS_REGION

print_info "✓ Stack SNS eliminado exitosamente"

# Eliminar archivo de outputs si existe
SCRIPT_DIR="$(dirname "$0")"
OUTPUT_FILE="$SCRIPT_DIR/../outputs/sns-${ENVIRONMENT}.txt"
if [ -f "$OUTPUT_FILE" ]; then
    rm "$OUTPUT_FILE"
    print_info "Archivo de outputs eliminado: $OUTPUT_FILE"
fi

print_info ""
print_info "=========================================="
print_info "✓ ELIMINACIÓN COMPLETADA"
print_info "=========================================="
print_info ""
print_info "El SNS Topic ha sido eliminado completamente"
print_info ""
print_info "Si necesita recrearlo, ejecute:"
print_info "  ./deploy-sns.sh $ENVIRONMENT [mscorreos-endpoint-url]"
print_info ""

