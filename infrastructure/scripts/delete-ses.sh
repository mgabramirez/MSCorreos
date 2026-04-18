#!/bin/bash

# Script para eliminar la configuración de Amazon SES para MSCorreos
# Uso: ./delete-ses.sh [dev|test|prod]

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
    exit 1
fi

ENVIRONMENT=$1
AWS_REGION="us-east-1"

# Validar ambiente
if [[ ! "$ENVIRONMENT" =~ ^(dev|test|prod)$ ]]; then
    print_error "Ambiente inválido. Debe ser: dev, test o prod"
    exit 1
fi

print_warning "⚠️  ADVERTENCIA: Esta operación eliminará toda la configuración de SES ⚠️"
print_warning "Ambiente: $ENVIRONMENT"
print_warning "Región: $AWS_REGION"
print_warning ""
read -p "¿Está seguro de que desea continuar? (escriba 'yes' para confirmar): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    print_info "Operación cancelada"
    exit 0
fi

SES_STACK_NAME="mscorreos-ses-config-${ENVIRONMENT}"
SNS_STACK_NAME="mscorreos-sns-tracking-${ENVIRONMENT}"

# Eliminar stack SES
print_info "Eliminando stack SES: $SES_STACK_NAME"
if aws cloudformation describe-stacks --stack-name $SES_STACK_NAME --region $AWS_REGION > /dev/null 2>&1; then
    aws cloudformation delete-stack \
        --stack-name $SES_STACK_NAME \
        --region $AWS_REGION
    
    print_info "Esperando a que se complete la eliminación del stack SES..."
    aws cloudformation wait stack-delete-complete \
        --stack-name $SES_STACK_NAME \
        --region $AWS_REGION
    
    print_info "Stack SES eliminado exitosamente"
else
    print_warning "Stack SES no existe"
fi

# Eliminar stack SNS
print_info "Eliminando stack SNS: $SNS_STACK_NAME"
if aws cloudformation describe-stacks --stack-name $SNS_STACK_NAME --region $AWS_REGION > /dev/null 2>&1; then
    aws cloudformation delete-stack \
        --stack-name $SNS_STACK_NAME \
        --region $AWS_REGION
    
    print_info "Esperando a que se complete la eliminación del stack SNS..."
    aws cloudformation wait stack-delete-complete \
        --stack-name $SNS_STACK_NAME \
        --region $AWS_REGION
    
    print_info "Stack SNS eliminado exitosamente"
else
    print_warning "Stack SNS no existe"
fi

print_info ""
print_info "=========================================="
print_info "ELIMINACIÓN COMPLETADA"
print_info "=========================================="
print_info ""
print_warning "NOTA: Las identidades verificadas (dominio y emails) NO se eliminan automáticamente"
print_warning "Si desea eliminarlas, hágalo manualmente desde la consola de SES"
print_info ""
