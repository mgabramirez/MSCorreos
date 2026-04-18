#!/bin/bash

# Script para desplegar CloudWatch (Log Groups, Métricas y Alarmas) usando CloudFormation
# Uso: ./deploy-cloudwatch.sh [dev|test|prod] [email-opcional]

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
    echo "Uso: $0 [dev|test|prod] [email-opcional]"
    exit 1
fi

ENVIRONMENT=$1
ALARM_EMAIL=${2:-""}
STACK_NAME="mscorreos-cloudwatch-${ENVIRONMENT}"
TEMPLATE_FILE="../cloudformation/cloudwatch.yaml"
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

echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Despliegue de CloudWatch para MSCorreos                      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Configuración:${NC}"
echo "  Stack Name: $STACK_NAME"
echo "  Environment: $ENVIRONMENT"
echo "  Region: $REGION"
if [ -n "$ALARM_EMAIL" ]; then
    echo "  Alarm Email: $ALARM_EMAIL"
else
    echo "  Alarm Email: No configurado (solo se crearán alarmas sin notificaciones)"
fi
echo ""

# Validar template
echo -e "${YELLOW}[1/4] Validando template de CloudFormation...${NC}"
aws cloudformation validate-template \
    --template-body file://$TEMPLATE_FILE \
    --region $REGION > /dev/null

if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Template inválido${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Template válido${NC}"
echo ""

# Preparar parámetros
PARAMETERS="ParameterKey=Environment,ParameterValue=$ENVIRONMENT"
if [ -n "$ALARM_EMAIL" ]; then
    PARAMETERS="$PARAMETERS ParameterKey=AlarmEmail,ParameterValue=$ALARM_EMAIL"
fi

# Verificar si el stack ya existe
echo -e "${YELLOW}[2/4] Verificando si el stack existe...${NC}"
STACK_EXISTS=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION 2>&1 || true)

if echo "$STACK_EXISTS" | grep -q "does not exist"; then
    # Crear nuevo stack
    echo -e "${YELLOW}[3/4] Creando nuevo stack...${NC}"
    aws cloudformation create-stack \
        --stack-name $STACK_NAME \
        --template-body file://$TEMPLATE_FILE \
        --parameters $PARAMETERS \
        --region $REGION \
        --tags Key=Application,Value=MSCorreos Key=Environment,Value=$ENVIRONMENT

    echo -e "${YELLOW}Esperando a que el stack se cree...${NC}"
    aws cloudformation wait stack-create-complete \
        --stack-name $STACK_NAME \
        --region $REGION

    echo -e "${GREEN}✓ Stack creado exitosamente${NC}"
else
    # Actualizar stack existente
    echo -e "${YELLOW}[3/4] Actualizando stack existente...${NC}"
    UPDATE_OUTPUT=$(aws cloudformation update-stack \
        --stack-name $STACK_NAME \
        --template-body file://$TEMPLATE_FILE \
        --parameters $PARAMETERS \
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
echo -e "${YELLOW}[4/4] Obteniendo información del despliegue...${NC}"
echo ""

# Obtener outputs del stack
echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Outputs del Stack                                             ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION \
    --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
    --output table

echo ""
echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Recursos Creados                                              ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${BLUE}Log Groups:${NC}"
echo "  • /aws/mscorreos/${ENVIRONMENT}"
echo "  • /aws/mscorreos/${ENVIRONMENT}/sqs-consumer"
echo "  • /aws/mscorreos/${ENVIRONMENT}/email-service"
echo "  • /aws/mscorreos/${ENVIRONMENT}/sns-listener"
echo "  • /aws/mscorreos/${ENVIRONMENT}/blacklist-service"
echo ""
echo -e "${BLUE}Alarmas CloudWatch:${NC}"
echo "  • mscorreos-error-rate-${ENVIRONMENT} (Tasa de errores > 5%)"
echo "  • mscorreos-processing-time-${ENVIRONMENT} (Tiempo procesamiento > 5s)"
echo "  • mscorreos-blacklist-size-${ENVIRONMENT} (Lista negra > 1000 correos)"
echo "  • mscorreos-dlq-high-messages-${ENVIRONMENT} (DLQ > 10 mensajes)"
echo "  • mscorreos-consecutive-failures-${ENVIRONMENT} (Fallos consecutivos)"
echo "  • mscorreos-high-blocked-emails-${ENVIRONMENT} (Alta tasa de bloqueos)"
echo ""
echo -e "${BLUE}Dashboard:${NC}"
echo "  • MSCorreos-${ENVIRONMENT}"
echo ""

# Verificar alarmas
echo -e "${YELLOW}Verificando alarmas creadas...${NC}"
ALARM_COUNT=$(aws cloudwatch describe-alarms \
    --alarm-name-prefix "mscorreos-" \
    --region $REGION \
    --query 'length(MetricAlarms)' \
    --output text)

echo -e "${GREEN}✓ Total de alarmas configuradas: $ALARM_COUNT${NC}"
echo ""

# Mostrar información adicional
echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Próximos Pasos                                                ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo "1. Configurar la aplicación MSCorreos para enviar logs a CloudWatch:"
echo "   - Log Group: /aws/mscorreos/${ENVIRONMENT}"
echo ""
echo "2. Implementar CloudWatchMetricsService para publicar métricas personalizadas:"
echo "   - CorreosEnviados"
echo "   - CorreosFallidos"
echo "   - CorreosBloqueados"
echo "   - TiempoProcesamiento"
echo "   - TotalCorreosListaNegra"
echo ""
echo "3. Ver logs en CloudWatch:"
echo "   aws logs tail /aws/mscorreos/${ENVIRONMENT} --follow"
echo ""
echo "4. Ver alarmas:"
echo "   aws cloudwatch describe-alarms --alarm-name-prefix mscorreos- --region $REGION"
echo ""
echo "5. Acceder al Dashboard:"
DASHBOARD_URL=$(aws cloudformation describe-stacks \
    --stack-name $STACK_NAME \
    --region $REGION \
    --query 'Stacks[0].Outputs[?OutputKey==`DashboardURL`].OutputValue' \
    --output text)
echo "   $DASHBOARD_URL"
echo ""

if [ -n "$ALARM_EMAIL" ]; then
    echo -e "${YELLOW}⚠ IMPORTANTE: Confirma la suscripción al SNS Topic${NC}"
    echo "   Revisa tu email ($ALARM_EMAIL) y confirma la suscripción"
    echo "   para recibir notificaciones de alarmas."
    echo ""
fi

echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  Despliegue Completado Exitosamente                           ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
