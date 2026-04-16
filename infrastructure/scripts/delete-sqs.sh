#!/bin/bash

# Script para eliminar las colas SQS usando CloudFormation
# Uso: ./delete-sqs.sh [dev|test|prod]

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Validar argumentos
if [ $# -eq 0 ]; then
    echo -e "${RED}Error: Debe especificar el ambiente (dev, test, prod)${NC}"
    echo "Uso: $0 [dev|test|prod]"
    exit 1
fi

ENVIRONMENT=$1
STACK_NAME="mscorreos-sqs-${ENVIRONMENT}"
REGION="us-east-1"

# Validar ambiente
if [[ ! "$ENVIRONMENT" =~ ^(dev|test|prod)$ ]]; then
    echo -e "${RED}Error: Ambiente inválido. Use: dev, test o prod${NC}"
    exit 1
fi

echo -e "${YELLOW}¿Está seguro que desea eliminar el stack de SQS para ambiente: ${ENVIRONMENT}?${NC}"
echo "Stack Name: $STACK_NAME"
echo "Region: $REGION"
echo ""
read -p "Escriba 'yes' para confirmar: " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo -e "${YELLOW}Operación cancelada${NC}"
    exit 0
fi

# Verificar si el stack existe
STACK_EXISTS=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION 2>&1 || true)

if echo "$STACK_EXISTS" | grep -q "does not exist"; then
    echo -e "${YELLOW}El stack no existe${NC}"
    exit 0
fi

# Eliminar stack
echo -e "${YELLOW}Eliminando stack...${NC}"
aws cloudformation delete-stack \
    --stack-name $STACK_NAME \
    --region $REGION

echo -e "${YELLOW}Esperando a que el stack se elimine...${NC}"
aws cloudformation wait stack-delete-complete \
    --stack-name $STACK_NAME \
    --region $REGION

echo -e "${GREEN}Stack eliminado exitosamente${NC}"
