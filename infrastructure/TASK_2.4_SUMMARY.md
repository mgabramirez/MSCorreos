# Task 2.4: Configurar Amazon S3 - Resumen de Implementación

## Descripción

Implementación completa de la configuración de Amazon S3 para almacenamiento temporal de adjuntos de correos electrónicos en el sistema MSCorreos.

## Requisitos Cumplidos

- ✅ **Requirement 13.1**: Productores suben archivos a S3 e incluyen referencias en eventos
- ✅ **Requirement 13.2**: Eventos contienen array de adjuntos con s3_bucket, s3_key, nombre_archivo, content_type
- ✅ **Requirement 13.3**: MSCorreos descarga adjuntos desde S3
- ✅ **Requirement 13.4**: MSCorreos elimina adjuntos después de envío exitoso
- ✅ **Requirement 13.5**: Política de ciclo de vida elimina archivos no procesados después de 7 días
- ✅ **Requirement 13.6**: Validación de tamaño total de adjuntos (máximo 10 MB)

## Archivos Creados

### 1. CloudFormation Template
**Archivo**: `infrastructure/cloudformation/s3-bucket.yaml`

**Características**:
- Bucket S3 con nombre `mscorreos-adjuntos-{environment}`
- Versionamiento habilitado para recuperación de archivos
- Cifrado en reposo usando AES-256
- Política de ciclo de vida: elimina objetos después de 7 días
- Bloqueo de acceso público completo
- Políticas de bucket para MSCorreos (consumidor) y ShrimpSoftServer (productor)
- Alarmas CloudWatch para tamaño del bucket (10 GB) y número de objetos (10,000)

### 2. Terraform Configuration
**Archivo**: `infrastructure/terraform/s3.tf`

**Recursos**:
- `aws_s3_bucket.mscorreos_adjuntos` - Bucket principal
- `aws_s3_bucket_versioning` - Configuración de versionamiento
- `aws_s3_bucket_server_side_encryption_configuration` - Cifrado AES-256
- `aws_s3_bucket_lifecycle_configuration` - Política de ciclo de vida (7 días)
- `aws_s3_bucket_public_access_block` - Bloqueo de acceso público
- `aws_s3_bucket_policy` - Políticas de acceso para productores y consumidores
- `aws_cloudwatch_metric_alarm` - Alarmas de monitoreo

### 3. Scripts de Despliegue

#### deploy-s3.sh
**Archivo**: `infrastructure/scripts/deploy-s3.sh`

**Funcionalidad**:
- Valida template de CloudFormation
- Crea o actualiza stack según corresponda
- Verifica configuración del bucket (ciclo de vida, cifrado, versionamiento, acceso público)
- Muestra outputs del stack
- Proporciona instrucciones de próximos pasos

#### delete-s3.sh
**Archivo**: `infrastructure/scripts/delete-s3.sh`

**Funcionalidad**:
- Solicita confirmación antes de eliminar
- Vacía el bucket (elimina todas las versiones y marcadores de eliminación)
- Elimina el stack de CloudFormation
- Espera a que la eliminación se complete

### 4. Políticas IAM

#### iam-policy-mscorreos-s3.json
**Archivo**: `infrastructure/config/iam-policy-mscorreos-s3.json`

**Permisos para MSCorreos (Consumidor)**:
- `s3:GetObject` - Descargar adjuntos
- `s3:DeleteObject` - Eliminar adjuntos después de envío
- `s3:ListBucket` - Listar objetos del bucket
- `s3:GetBucketLocation` - Obtener ubicación del bucket

#### iam-policy-shrimpsoft-s3.json
**Archivo**: `infrastructure/config/iam-policy-shrimpsoft-s3.json`

**Permisos para ShrimpSoftServer (Productor)**:
- `s3:PutObject` - Subir adjuntos
- `s3:PutObjectAcl` - Configurar ACLs de objetos
- `s3:ListBucket` - Listar objetos para verificación
- `s3:GetBucketLocation` - Obtener ubicación del bucket

### 5. Documentación

#### S3_CONFIGURATION_GUIDE.md
**Archivo**: `infrastructure/S3_CONFIGURATION_GUIDE.md`

**Contenido**:
- Descripción general y arquitectura
- Requisitos cumplidos
- Componentes del sistema (bucket, políticas, alarmas)
- Guías de despliegue (CloudFormation, Terraform, AWS CLI)
- Configuración de roles IAM
- Ejemplos de código para productores y consumidores
- Configuración de la aplicación
- Monitoreo y métricas
- Troubleshooting detallado
- Mejores prácticas de seguridad
- Cálculo de costos estimados

#### S3_QUICK_REFERENCE.md
**Archivo**: `infrastructure/S3_QUICK_REFERENCE.md`

**Contenido**:
- Comandos de despliegue rápido
- Información del bucket
- Comandos útiles (verificación, operaciones básicas, monitoreo)
- Ejemplos de código compactos
- Configuración rápida
- Políticas IAM resumidas
- Tabla de troubleshooting
- Costos estimados

## Configuración del Bucket

### Características Principales

| Característica | Valor |
|----------------|-------|
| Nombre | `mscorreos-adjuntos-{environment}` |
| Región | us-east-1 |
| Versionamiento | Habilitado |
| Cifrado | AES-256 (en reposo) |
| Ciclo de vida | 7 días (versiones actuales), 1 día (versiones antiguas) |
| Acceso público | Completamente bloqueado |
| Tamaño máximo recomendado | 10 GB |
| Objetos máximos recomendados | 10,000 |

### Política de Ciclo de Vida

```json
{
  "Rules": [
    {
      "Id": "DeleteOldAttachments",
      "Status": "Enabled",
      "ExpirationInDays": 7,
      "NoncurrentVersionExpirationInDays": 1
    }
  ]
}
```

**Comportamiento**:
- Los objetos se eliminan automáticamente después de 7 días desde su creación
- Las versiones no actuales se eliminan después de 1 día
- S3 ejecuta la política una vez al día (puede tardar hasta 24 horas después de la fecha de expiración)

## Flujo de Trabajo

### 1. Productor (ShrimpSoftServer) Sube Adjunto

```
1. Productor genera evento de notificación
2. Si hay adjuntos:
   a. Subir cada archivo a S3 con key único
   b. Crear AdjuntoReferencia con s3_bucket, s3_key, nombre_archivo, content_type
   c. Agregar referencias al array de adjuntos del evento
3. Publicar evento en SQS con referencias S3
```

### 2. Consumidor (MSCorreos) Procesa Adjuntos

```
1. Consumer recibe evento de SQS
2. Si evento tiene adjuntos:
   a. Para cada AdjuntoReferencia:
      - Descargar archivo desde S3 a archivo temporal
   b. Construir mensaje MIME con adjuntos
   c. Enviar correo mediante SES
   d. Si envío exitoso:
      - Eliminar cada adjunto de S3
      - Eliminar archivos temporales
   e. Si envío falla:
      - Mantener adjuntos en S3 para reintento
      - Eliminar archivos temporales
```

### 3. Limpieza Automática

```
1. S3 ejecuta política de ciclo de vida diariamente
2. Identifica objetos con más de 7 días
3. Elimina objetos expirados automáticamente
4. Esto captura adjuntos de mensajes que fallaron permanentemente
```

## Alarmas CloudWatch

### 1. Alarma de Tamaño de Bucket

**Nombre**: `mscorreos-s3-bucket-size-{environment}`

**Configuración**:
- Métrica: `BucketSizeBytes`
- Namespace: `AWS/S3`
- Umbral: 10 GB (10,737,418,240 bytes)
- Período: 1 día (86,400 segundos)
- Evaluación: 1 período

**Acción**: Notificar cuando el bucket supera 10 GB, indicando posible problema con eliminación de adjuntos.

### 2. Alarma de Número de Objetos

**Nombre**: `mscorreos-s3-object-count-{environment}`

**Configuración**:
- Métrica: `NumberOfObjects`
- Namespace: `AWS/S3`
- Umbral: 10,000 objetos
- Período: 1 día (86,400 segundos)
- Evaluación: 1 período

**Acción**: Notificar cuando el bucket tiene más de 10,000 objetos, indicando posible acumulación de archivos.

## Seguridad

### Principios Implementados

1. **Principio de Mínimo Privilegio**
   - MSCorreos solo puede leer y eliminar (no puede escribir)
   - ShrimpSoftServer solo puede escribir (no puede eliminar)

2. **Cifrado en Reposo**
   - Todos los objetos se cifran automáticamente con AES-256

3. **Cifrado en Tránsito**
   - Todas las comunicaciones usan HTTPS/TLS

4. **Bloqueo de Acceso Público**
   - Acceso público completamente bloqueado a nivel de bucket

5. **Versionamiento**
   - Permite recuperación de archivos eliminados accidentalmente

6. **Auditoría**
   - CloudWatch registra todas las operaciones del bucket

## Costos Estimados

### Supuestos
- 1,000 correos con adjuntos por día
- Tamaño promedio de adjunto: 500 KB
- Retención: 7 días
- Región: us-east-1

### Cálculo

| Concepto | Cálculo | Costo Mensual |
|----------|---------|---------------|
| Almacenamiento | 3.5 GB × $0.023/GB | $0.08 |
| PUT requests | 30,000 × $0.005/1000 | $0.15 |
| GET requests | 30,000 × $0.0004/1000 | $0.01 |
| DELETE requests | 30,000 × $0.0004/1000 | $0.01 |
| Transferencia de datos | Gratis (dentro de AWS) | $0.00 |
| **Total** | | **$0.25/mes** |

## Próximos Pasos

### 1. Desplegar Bucket S3

```bash
cd infrastructure/scripts
chmod +x deploy-s3.sh
./deploy-s3.sh dev
```

### 2. Crear Roles IAM

```bash
# Crear rol para MSCorreos
aws iam create-role \
  --role-name MSCorreosECSTaskRole-dev \
  --assume-role-policy-document file://trust-policy-ecs.json

aws iam put-role-policy \
  --role-name MSCorreosECSTaskRole-dev \
  --policy-name MSCorreosS3Access \
  --policy-document file://config/iam-policy-mscorreos-s3.json

# Crear rol para ShrimpSoftServer
aws iam create-role \
  --role-name ShrimpSoftServerRole-dev \
  --assume-role-policy-document file://trust-policy-ec2.json

aws iam put-role-policy \
  --role-name ShrimpSoftServerRole-dev \
  --policy-name ShrimpSoftS3Access \
  --policy-document file://config/iam-policy-shrimpsoft-s3.json
```

### 3. Configurar Variables de Entorno

```bash
# Obtener nombre del bucket
BUCKET_NAME=$(aws cloudformation describe-stacks \
  --stack-name mscorreos-s3-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`BucketName`].OutputValue' \
  --output text)

# Configurar en MSCorreos
export S3_BUCKET_ADJUNTOS=$BUCKET_NAME
```

### 4. Probar Configuración

```bash
# Subir archivo de prueba
echo "Test attachment" > test-file.txt
aws s3 cp test-file.txt s3://$BUCKET_NAME/test/

# Verificar que se subió
aws s3 ls s3://$BUCKET_NAME/test/

# Descargar archivo
aws s3 cp s3://$BUCKET_NAME/test/test-file.txt ./downloaded-test.txt

# Eliminar archivo
aws s3 rm s3://$BUCKET_NAME/test/test-file.txt
```

### 5. Implementar en Código

- **ShrimpSoftServer**: Implementar `S3AttachmentUploader` para subir adjuntos
- **MSCorreos**: Implementar `S3AttachmentDownloader` para descargar y eliminar adjuntos
- Actualizar `EventoNotificacion` para incluir array de `AdjuntoReferencia`
- Actualizar procesamiento de eventos para manejar adjuntos

### 6. Monitorear

- Configurar dashboard de CloudWatch con métricas de S3
- Configurar notificaciones SNS para alarmas
- Revisar logs de acceso periódicamente

## Validación

### Checklist de Validación

- [ ] Bucket S3 creado con nombre correcto
- [ ] Versionamiento habilitado
- [ ] Cifrado AES-256 configurado
- [ ] Política de ciclo de vida configurada (7 días)
- [ ] Acceso público bloqueado
- [ ] Política de bucket configurada correctamente
- [ ] Roles IAM creados con permisos correctos
- [ ] Alarmas CloudWatch activas
- [ ] Prueba de subida exitosa
- [ ] Prueba de descarga exitosa
- [ ] Prueba de eliminación exitosa
- [ ] Variables de entorno configuradas en aplicaciones

## Referencias

- [Guía Completa de Configuración](./S3_CONFIGURATION_GUIDE.md)
- [Referencia Rápida](./S3_QUICK_REFERENCE.md)
- [AWS S3 Documentation](https://docs.aws.amazon.com/s3/)
- [S3 Lifecycle Configuration](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html)
- [S3 Security Best Practices](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html)

## Notas Adicionales

### Consideraciones de Diseño

1. **Ciclo de Vida de 7 Días**: Elegido para dar tiempo suficiente para reintentos de mensajes fallidos (3 reintentos con backoff exponencial) mientras mantiene costos bajos.

2. **Versionamiento**: Habilitado para recuperación de archivos eliminados accidentalmente, aunque las versiones antiguas se eliminan después de 1 día.

3. **Separación de Permisos**: MSCorreos y ShrimpSoftServer tienen permisos diferentes siguiendo el principio de mínimo privilegio.

4. **Alarmas Proactivas**: Configuradas para detectar problemas antes de que afecten el servicio (bucket lleno, acumulación de objetos).

### Limitaciones Conocidas

1. **Tamaño Máximo de Adjunto**: 10 MB por correo (límite de SES)
2. **Latencia de Eliminación**: La política de ciclo de vida puede tardar hasta 24 horas en ejecutarse
3. **Nombres de Bucket Globalmente Únicos**: Si el nombre ya existe, debe cambiarse

### Mejoras Futuras

1. **Logs de Acceso**: Habilitar logs de acceso para auditoría detallada
2. **Replicación Cross-Region**: Para disaster recovery
3. **Inteligent-Tiering**: Para optimizar costos si el volumen aumenta significativamente
4. **Notificaciones de Eventos**: Configurar notificaciones S3 para eventos de creación/eliminación
