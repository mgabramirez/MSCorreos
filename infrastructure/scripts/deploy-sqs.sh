#!/bin/bash

# Script para desplegar las colas SQS usando CloudFormation
# Uso: ./deploy-sqs.sh [dev|test|prod]

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
TEMPLATE_FILE="../cloudformation/sqs-queues.yaml"
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

echo -e "${YELLOW}Desplegando stack de SQS para ambiente: ${ENVIRONMENT}${NC}"
echo "Stack Name: $STACK_NAME"
echo "Region: $REGION"
echo ""

# Validar template
echo -e "${YELLOW}Validando template de CloudFormation...${NC}"
aws cloudformation validate-template \
    --template-body file://$TEMPLATE_FILE \
    --region $REGION

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Template inválido${NC}"
    exit 1
fi

echo -e "${GREEN}Template válido${NC}"
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

    echo -e "${GREEN}Stack creado exitosamente${NC}"
else
    # Actualizar stack existente
    echo -e "${YELLOW}Actualizando stack existente...${NC}"
    UPDATE_OUTPUT=$(aws cloudformation update-stack \
        --stack-name $STACK_NAME \
        --template-body file://$TEMPLATE_FILE \
        --parameters ParameterKey=Environment,ParameterValue=$ENVIRONMENT \
        --region $REGION 2>&1 || true)

    if echo "$UPDATE_OUTPUT" | grep -q "No updates are to be performed"; then
        echo -e "${GREEN}No hay cambios para aplicar${NC}"
    else
        echo -e "${YELLOW}Esperando a que el stack se actualice...${NC}"
        aws cloudformation wait stack-update-complete \
            --stack-name $STACK_NAME \
            --region $REGION

        echo -e "${GREEN}Stack actualizado exitosamente${NC}"
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
echo -e "${GREEN}Despliegue completado exitosamente${NC}"
