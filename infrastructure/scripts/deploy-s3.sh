#!/bin/bash

# Script para desplegar el bucket S3 usando CloudFormation
# Uso: ./deploy-s3.sh [dev|test|prod]

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
TEMPLATE_FILE="../cloudformation/s3-bucket.yaml"
REGION="us-east-1"

# Validar ambiente
if [[ ! "$ENVIRONMENT" =~ ^(dev|test|prod)$ ]]; then
    echo -e "${RED}Error: Ambiente inválido. Use: dev, test o prod${NC}"
    exit 1
fi

# Validar que el archivo de template existe
if [ ! -f "$TEMPLATE_FILE" ]; then
    echo -e "${RED}Error: No se encuentra el archivo de template: $TEMPLATE_FILE${NC}"
    exit 1
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Despliegue de S3 Bucket - MSCorreos  ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Desplegando stack de S3 para ambiente: ${ENVIRONMENT}${NC}"
echo "Stack Name: $STACK_NAME"
echo "Region: $REGION"
echo ""

# Validar template
echo -e "${YELLOW}Validando template de CloudFormation...${NC}"
aws cloudformation validate-template \
    --template-body file://$TEMPLATE_FILE \
    --region $REGION > /dev/null

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Template inválido${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Template válido${NC}"
echo ""

# Verificar si el stack ya existe
STACK_EXISTS=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION 2>&1 || true)

if echo "$STACK_EXISTS" | grep -q "does not exist"; then
    # Crear nuevo stack
    echo -e "${YELLOW}Creando nuevo stack...${NC}"
    aws cloudformation create-stack \
        --stack-name $STACK_NAME \
        --template-body file://$TEMPLATE_FILE \
        --parameters ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
        --region $REGION \
        --tags Key=Application,Value=MSCorreos Key=Environment,Value=$ENVIRONMENT

    echo -e "${YELLOW}Esperando a que el stack se cree...${NC}"
    aws cloudformation wait stack-create-complete \
        --stack-name $STACK_NAME \
        --region $REGION

    echo -e "${GREEN}✓ Stack creado exitosamente${NC}"
else
    # Actualizar stack existente
    echo -e "${YELLOW}Actualizando stack existente...${NC}"
    UPDATE_OUTPUT=$(aws cloudformation update-stack \
        --stack-name $STACK_NAME \
        --template-body file://$TEMPLATE_FILE \
        --parameters ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
        --region $REGION 2>&1 || true)

    if echo "$UPDATE_OUTPUT" | grep -q "No updates are to be performed"; then
        echo -e "${GREEN}✓ No hay cambios para aplicar${NC}"
    else
        echo -e "${YELLOW}Esperando a que el stack se actualice...${NC}"
        aws cloudformation wait stack-update-complete \
            --stack-name $STACK_NAME \
            --region $REGION

        echo -e "${GREEN}✓ Stack actualizado exitosamente${NC}"
    fi
fi

echo ""
echo -e "${GREEN}=== Outputs del Stack ===${NC}"
aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
    --output table

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✓ Despliegue completado exitosamente${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Obtener nombre del bucket
BUCKET_NAME=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION \
    --query 'Stacks[0].Outputs[?OutputKey==`BucketName`].OutputValue' \
    --output text)

echo -e "${YELLOW}Información del Bucket:${NC}"
echo "  Nombre: $BUCKET_NAME"
echo "  Región: $REGION"
echo ""

# Verificar configuración del bucket
echo -e "${YELLOW}Verificando configuración del bucket...${NC}"

# Verificar política de ciclo de vida
echo -e "${BLUE}Política de ciclo de vida:${NC}"
aws s3api get-bucket-lifecycle-configuration \
    --bucket $BUCKET_NAME \
    --region $REGION \
    --output json | jq '.Rules[] | {Id, Status, ExpirationDays: .Expiration.Days}'

# Verificar cifrado
echo ""
echo -e "${BLUE}Configuración de cifrado:${NC}"
aws s3api get-bucket-encryption \
    --bucket $BUCKET_NAME \
    --region $REGION \
    --output json | jq '.Rules[0].ApplyServerSideEncryptionByDefault'

# Verificar versionamiento
echo ""
echo -e "${BLUE}Configuración de versionamiento:${NC}"
aws s3api get-bucket-versioning \
    --bucket $BUCKET_NAME \
    --region $REGION

# Verificar bloqueo de acceso público
echo ""
echo -e "${BLUE}Bloqueo de acceso público:${NC}"
aws s3api get-public-access-block \
    --bucket $BUCKET_NAME \
    --region $REGION \
    --output json | jq '.PublicAccessBlockConfiguration'

echo ""
echo -e "${GREEN}✓ Verificación completada${NC}"
echo ""

# Instrucciones para configurar variables de entorno
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}  Próximos Pasos${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""
echo "1. Configure la variable de entorno en MSCorreos:"
echo ""
echo "   export S3_BUCKET_ADJUNTOS=$BUCKET_NAME"
echo ""
echo "2. O agregue al application.yml:"
echo ""
echo "   aws:"
echo "     s3:"
echo "       bucket: $BUCKET_NAME"
echo ""
echo "3. Asegúrese de que los roles IAM existen:"
echo "   - MSCorreosECSTaskRole-${ENVIRONMENT} (Consumidor)"
echo "   - ShrimpSoftServerRole-${ENVIRONMENT} (Productor)"
echo ""
echo "4. Pruebe subir un archivo de prueba:"
echo ""
echo "   aws s3 cp test-file.txt s3://$BUCKET_NAME/test/"
echo ""
echo "5. Verifique que el archivo se elimina después de 7 días"
echo ""
echo -e "${GREEN}Para más información, consulte: infrastructure/S3_CONFIGURATION_GUIDE.md${NC}"
echo ""
