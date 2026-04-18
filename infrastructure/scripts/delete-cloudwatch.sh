#!/bin/bash

# Script para eliminar la configuración de CloudWatch usando CloudFormation
# Uso: ./delete-cloudwatch.sh [dev|test|prod]

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
STACK_NAME="mscorreos-cloudwatch-${ENVIRONMENT}"
REGION="us-east-1"

# Validar ambiente
if [[ ! "$ENVIRONMENT" =~ ^(dev|test|prod)$ ]]; then
    echo -e "${RED}Error: Ambiente inválido. Use: dev, test o prod${NC}"
    exit 1
fi

echo -e "${YELLOW}⚠ ADVERTENCIA: Esta operación eliminará:${NC}"
echo "  • Todos los Log Groups de MSCorreos (los logs se perderán)"
echo "  • Todas las alarmas de CloudWatch configuradas"
echo "  • El Dashboard de CloudWatch"
echo "  • El SNS Topic de notificaciones de alarmas"
echo ""
echo "Stack a eliminar: $STACK_NAME"
echo "Region: $REGION"
echo ""

# Confirmar eliminación
read -p "¿Está seguro de que desea continuar? (escriba 'yes' para confirmar): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo -e "${YELLOW}Operación cancelada${NC}"
    exit 0
fi

# Verificar si el stack existe
echo -e "${YELLOW}Verificando si el stack existe...${NC}"
STACK_EXISTS=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION 2>&1 || true)

if echo "$STACK_EXISTS" | grep -q "does not exist"; then
    echo -e "${YELLOW}El stack $STACK_NAME no existe${NC}"
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
echo ""
echo -e "${GREEN}Recursos eliminados:${NC}"
echo "  ✓ Log Groups"
echo "  ✓ Metric Filters"
echo "  ✓ CloudWatch Alarms"
echo "  ✓ SNS Topic"
echo "  ✓ Dashboard"
