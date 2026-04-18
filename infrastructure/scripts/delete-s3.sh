#!/bin/bash

# Script para eliminar el bucket S3 y su stack de CloudFormation
# Uso: ./delete-s3.sh [dev|test|prod]

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Validar argumentos
if [ $# -eq 0 ]; then
    echo -e "${RED}Error: Debe especificar el ambiente (dev, test, prod)${NC}"
    echo "Uso: $0 [dev|test|prod]"
    exit 1
fi

ENVIRONMENT=$1
STACK_NAME="mscorreos-s3-${ENVIRONMENT}"
REGION="us-east-1"

# Validar ambiente
if [[ ! "$ENVIRONMENT" =~ ^(dev|test|prod)$ ]]; then
    echo -e "${RED}Error: Ambiente inválido. Use: dev, test o prod${NC}"
    exit 1
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Eliminación de S3 Bucket - MSCorreos ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${RED}⚠️  ADVERTENCIA: Esta operación eliminará el bucket S3 y todos sus archivos${NC}"
echo ""
echo "Stack Name: $STACK_NAME"
echo "Region: $REGION"
echo ""

# Confirmar eliminación
read -p "¿Está seguro de que desea continuar? (escriba 'yes' para confirmar): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo -e "${YELLOW}Operación cancelada${NC}"
    exit 0
fi

# Verificar si el stack existe
STACK_EXISTS=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION 2>&1 || true)

if echo "$STACK_EXISTS" | grep -q "does not exist"; then
    echo -e "${YELLOW}El stack $STACK_NAME no existe${NC}"
    exit 0
fi

# Obtener nombre del bucket
BUCKET_NAME=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION \
    --query 'Stacks[0].Outputs[?OutputKey==`BucketName`].OutputValue' \
    --output text)

echo ""
echo -e "${YELLOW}Bucket a eliminar: $BUCKET_NAME${NC}"
echo ""

# Vaciar el bucket antes de eliminarlo
echo -e "${YELLOW}Vaciando bucket...${NC}"

# Eliminar todas las versiones de objetos
aws s3api list-object-versions \
    --bucket $BUCKET_NAME \
    --region $REGION \
    --output json \
    --query 'Versions[].{Key:Key,VersionId:VersionId}' | \
    jq -r '.[] | "--key \"\(.Key)\" --version-id \"\(.VersionId)\""' | \
    while read -r args; do
        eval aws s3api delete-object --bucket $BUCKET_NAME --region $REGION $args
    done

# Eliminar marcadores de eliminación
aws s3api list-object-versions \
    --bucket $BUCKET_NAME \
    --region $REGION \
    --output json \
    --query 'DeleteMarkers[].{Key:Key,VersionId:VersionId}' | \
    jq -r '.[] | "--key \"\(.Key)\" --version-id \"\(.VersionId)\""' | \
    while read -r args; do
        eval aws s3api delete-object --bucket $BUCKET_NAME --region $REGION $args
    done

echo -e "${GREEN}✓ Bucket vaciado${NC}"
echo ""

# Eliminar el stack de CloudFormation
echo -e "${YELLOW}Eliminando stack de CloudFormation...${NC}"
aws cloudformation delete-stack \
    --stack-name $STACK_NAME \
    --region $REGION

echo -e "${YELLOW}Esperando a que el stack se elimine...${NC}"
aws cloudformation wait stack-delete-complete \
    --stack-name $STACK_NAME \
    --region $REGION

echo ""
echo -e "${GREEN}✓ Stack eliminado exitosamente${NC}"
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✓ Eliminación completada${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
