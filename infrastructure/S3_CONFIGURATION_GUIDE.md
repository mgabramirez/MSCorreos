# Guía de Configuración de Amazon S3 para MSCorreos

## Descripción General

Este documento describe la configuración completa de Amazon S3 para el sistema de notificaciones por correo electrónico MSCorreos. El bucket S3 se utiliza para almacenar temporalmente adjuntos de correos electrónicos antes de ser enviados.

## Arquitectura

```
┌─────────────────────┐
│ ShrimpSoftServer    │
│   (Productor)       │
└──────────┬──────────┘
           │ PUT Object
           ▼
┌─────────────────────────────────────┐
│   S3 Bucket: mscorreos-adjuntos     │
│                                     │
│  • Versionamiento: Habilitado      │
│  • Cifrado: AES-256                │
│  • Ciclo de vida: 7 días           │
│  • Acceso público: Bloqueado       │
└──────────┬──────────────────────────┘
           │ GET/DELETE Object
           ▼
┌─────────────────────┐
│    MSCorreos        │
│   (Consumidor)      │
└─────────────────────┘
```

## Requisitos Cumplidos

- **Requirement 13.1**: Productores suben archivos a S3 e incluyen referencias en eventos
- **Requirement 13.2**: Eventos contienen array de adjuntos con s3_bucket, s3_key, nombre_archivo, content_type
- **Requirement 13.3**: MSCorreos descarga adjuntos desde S3
- **Requirement 13.4**: MSCorreos elimina adjuntos después de envío exitoso
- **Requirement 13.5**: Política de ciclo de vida elimina archivos no procesados después de 7 días
- **Requirement 13.6**: Validación de tamaño total de adjuntos (máximo 10 MB)

## Componentes

### 1. Bucket S3

**Nombre**: `mscorreos-adjuntos-{environment}`

**Características**:
- **Versionamiento**: Habilitado para recuperación de archivos
- **Cifrado**: AES-256 en reposo
- **Ciclo de vida**: Elimina objetos después de 7 días
- **Acceso público**: Completamente bloqueado
- **Región**: us-east-1

### 2. Política de Ciclo de Vida

Elimina automáticamente:
- Versiones actuales después de 7 días
- Versiones no actuales después de 1 día

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

### 3. Políticas de Acceso

#### MSCorreos (Consumidor)
- `s3:GetObject` - Descargar adjuntos
- `s3:DeleteObject` - Eliminar adjuntos después de envío
- `s3:ListBucket` - Listar objetos del bucket

#### ShrimpSoftServer (Productor)
- `s3:PutObject` - Subir adjuntos
- `s3:PutObjectAcl` - Configurar ACLs
- `s3:ListBucket` - Listar objetos para verificación

### 4. Alarmas CloudWatch

#### Alarma de Tamaño de Bucket
- **Métrica**: BucketSizeBytes
- **Umbral**: 10 GB
- **Período**: 1 día
- **Acción**: Notificar cuando se supera el umbral

#### Alarma de Número de Objetos
- **Métrica**: NumberOfObjects
- **Umbral**: 10,000 objetos
- **Período**: 1 día
- **Acción**: Notificar cuando se supera el umbral

## Despliegue

### Opción 1: CloudFormation

```bash
cd infrastructure/scripts
chmod +x deploy-s3.sh
./deploy-s3.sh [dev|test|prod]
```

El script:
1. Valida el template de CloudFormation
2. Crea o actualiza el stack
3. Configura el bucket con todas las políticas
4. Verifica la configuración
5. Muestra los outputs del stack

### Opción 2: Terraform

```bash
cd infrastructure/terraform

# Inicializar Terraform
terraform init

# Planificar cambios
terraform plan -var="environment=dev" -var="aws_account_id=YOUR_ACCOUNT_ID"

# Aplicar cambios
terraform apply -var="environment=dev" -var="aws_account_id=YOUR_ACCOUNT_ID"
```

### Opción 3: AWS CLI Manual

```bash
# Crear bucket
aws s3api create-bucket \
  --bucket mscorreos-adjuntos-dev \
  --region us-east-1

# Habilitar versionamiento
aws s3api put-bucket-versioning \
  --bucket mscorreos-adjuntos-dev \
  --versioning-configuration Status=Enabled

# Configurar cifrado
aws s3api put-bucket-encryption \
  --bucket mscorreos-adjuntos-dev \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'

# Configurar ciclo de vida
aws s3api put-bucket-lifecycle-configuration \
  --bucket mscorreos-adjuntos-dev \
  --lifecycle-configuration file://lifecycle-policy.json

# Bloquear acceso público
aws s3api put-public-access-block \
  --bucket mscorreos-adjuntos-dev \
  --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

## Configuración de Roles IAM

### Crear Rol para MSCorreos

```bash
# Crear rol
aws iam create-role \
  --role-name MSCorreosECSTaskRole-dev \
  --assume-role-policy-document file://trust-policy-ecs.json

# Adjuntar política S3
aws iam put-role-policy \
  --role-name MSCorreosECSTaskRole-dev \
  --policy-name MSCorreosS3Access \
  --policy-document file://config/iam-policy-mscorreos-s3.json
```

### Crear Rol para ShrimpSoftServer

```bash
# Crear rol
aws iam create-role \
  --role-name ShrimpSoftServerRole-dev \
  --assume-role-policy-document file://trust-policy-ec2.json

# Adjuntar política S3
aws iam put-role-policy \
  --role-name ShrimpSoftServerRole-dev \
  --policy-name ShrimpSoftS3Access \
  --policy-document file://config/iam-policy-shrimpsoft-s3.json
```

## Uso del Bucket

### Desde ShrimpSoftServer (Productor)

#### 1. Subir Adjunto

```java
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

public class S3AttachmentUploader {
    private final S3Client s3Client;
    private final String bucketName;
    
    public String uploadAttachment(File file, String contentType) {
        String key = generateKey(file.getName());
        
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .build();
        
        s3Client.putObject(request, RequestBody.fromFile(file));
        
        return key;
    }
    
    private String generateKey(String fileName) {
        String timestamp = Instant.now().toString();
        String uuid = UUID.randomUUID().toString();
        return String.format("adjuntos/%s/%s-%s", timestamp, uuid, fileName);
    }
}
```

#### 2. Crear Evento con Referencias S3

```java
public EventoNotificacion crearEventoConAdjuntos(List<File> adjuntos) {
    List<AdjuntoReferencia> referencias = new ArrayList<>();
    
    for (File adjunto : adjuntos) {
        String key = uploadAttachment(adjunto, detectContentType(adjunto));
        
        AdjuntoReferencia ref = new AdjuntoReferencia();
        ref.setS3Bucket(bucketName);
        ref.setS3Key(key);
        ref.setNombreArchivo(adjunto.getName());
        ref.setContentType(detectContentType(adjunto));
        
        referencias.add(ref);
    }
    
    EventoNotificacion evento = new EventoNotificacion();
    evento.setAdjuntos(referencias);
    // ... configurar otros campos
    
    return evento;
}
```

### Desde MSCorreos (Consumidor)

#### 1. Descargar Adjunto

```java
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.ResponseBytes;

public class S3AttachmentDownloader {
    private final S3Client s3Client;
    
    public File downloadAttachment(String bucket, String key) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
        
        ResponseBytes<?> objectBytes = s3Client.getObjectAsBytes(request);
        
        File tempFile = File.createTempFile("adjunto-", ".tmp");
        Files.write(tempFile.toPath(), objectBytes.asByteArray());
        
        return tempFile;
    }
}
```

#### 2. Eliminar Adjunto Después de Envío

```java
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

public void deleteAttachment(String bucket, String key) {
    DeleteObjectRequest request = DeleteObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .build();
    
    s3Client.deleteObject(request);
}
```

#### 3. Procesar Evento con Adjuntos

```java
public void procesarEventoConAdjuntos(EventoNotificacion evento) {
    List<File> adjuntosDescargados = new ArrayList<>();
    
    try {
        // Descargar todos los adjuntos
        for (AdjuntoReferencia ref : evento.getAdjuntos()) {
            File adjunto = downloadAttachment(ref.getS3Bucket(), ref.getS3Key());
            adjuntosDescargados.add(adjunto);
        }
        
        // Enviar correo con adjuntos
        enviarCorreo(evento, adjuntosDescargados);
        
        // Eliminar adjuntos de S3 después de envío exitoso
        for (AdjuntoReferencia ref : evento.getAdjuntos()) {
            deleteAttachment(ref.getS3Bucket(), ref.getS3Key());
        }
        
    } finally {
        // Limpiar archivos temporales
        for (File file : adjuntosDescargados) {
            file.delete();
        }
    }
}
```

## Configuración de la Aplicación

### application.yml (MSCorreos)

```yaml
aws:
  region: us-east-1
  s3:
    bucket: ${S3_BUCKET_ADJUNTOS:mscorreos-adjuntos-dev}
    max-attachment-size: 10485760  # 10 MB en bytes
```

### Variables de Entorno

```bash
export AWS_REGION=us-east-1
export S3_BUCKET_ADJUNTOS=mscorreos-adjuntos-dev
```

## Monitoreo

### Métricas Disponibles

#### Métricas de S3 (CloudWatch)

```bash
# Tamaño del bucket
aws cloudwatch get-metric-statistics \
  --namespace AWS/S3 \
  --metric-name BucketSizeBytes \
  --dimensions Name=BucketName,Value=mscorreos-adjuntos-dev Name=StorageType,Value=StandardStorage \
  --start-time $(date -u -d '1 day ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 86400 \
  --statistics Average

# Número de objetos
aws cloudwatch get-metric-statistics \
  --namespace AWS/S3 \
  --metric-name NumberOfObjects \
  --dimensions Name=BucketName,Value=mscorreos-adjuntos-dev Name=StorageType,Value=AllStorageTypes \
  --start-time $(date -u -d '1 day ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 86400 \
  --statistics Average
```

### Logs de Acceso (Opcional)

Para habilitar logs de acceso:

```bash
# Crear bucket para logs
aws s3api create-bucket \
  --bucket mscorreos-s3-logs-dev \
  --region us-east-1

# Configurar logging
aws s3api put-bucket-logging \
  --bucket mscorreos-adjuntos-dev \
  --bucket-logging-status '{
    "LoggingEnabled": {
      "TargetBucket": "mscorreos-s3-logs-dev",
      "TargetPrefix": "access-logs/"
    }
  }'
```

## Troubleshooting

### Error: Access Denied

**Problema**: No se puede subir/descargar archivos

**Solución**:
1. Verificar que el rol IAM tiene los permisos correctos
2. Verificar que la política del bucket permite el acceso
3. Verificar que el rol está asumido correctamente

```bash
# Verificar política del bucket
aws s3api get-bucket-policy --bucket mscorreos-adjuntos-dev

# Verificar política del rol
aws iam get-role-policy \
  --role-name MSCorreosECSTaskRole-dev \
  --policy-name MSCorreosS3Access
```

### Error: Bucket Already Exists

**Problema**: El nombre del bucket ya está en uso

**Solución**: Los nombres de buckets S3 son globalmente únicos. Cambie el nombre del bucket en el template.

### Archivos No Se Eliminan Después de 7 Días

**Problema**: La política de ciclo de vida no funciona

**Solución**:
1. Verificar que la política está configurada correctamente
2. Esperar hasta 24 horas después de la fecha de expiración
3. Verificar logs de S3

```bash
# Verificar política de ciclo de vida
aws s3api get-bucket-lifecycle-configuration \
  --bucket mscorreos-adjuntos-dev
```

### Bucket Lleno (Más de 10 GB)

**Problema**: El bucket supera el umbral de 10 GB

**Solución**:
1. Revisar si hay archivos antiguos que no se eliminaron
2. Verificar que MSCorreos está eliminando adjuntos después de envío
3. Considerar reducir el período de retención de 7 a 3 días

```bash
# Listar objetos más antiguos
aws s3api list-objects-v2 \
  --bucket mscorreos-adjuntos-dev \
  --query 'sort_by(Contents, &LastModified)[0:10].[Key, LastModified, Size]' \
  --output table
```

## Seguridad

### Mejores Prácticas

1. **Nunca hacer el bucket público**
2. **Usar cifrado en reposo** (AES-256 o KMS)
3. **Usar cifrado en tránsito** (HTTPS)
4. **Implementar principio de mínimo privilegio** en políticas IAM
5. **Habilitar versionamiento** para recuperación
6. **Configurar logs de acceso** para auditoría
7. **Rotar credenciales** regularmente
8. **Usar roles IAM** en lugar de access keys

### Validación de Archivos

Antes de subir archivos, validar:

```java
public void validateAttachment(File file) throws ValidationException {
    // Validar tamaño
    if (file.length() > 10 * 1024 * 1024) {
        throw new ValidationException("Archivo supera 10 MB");
    }
    
    // Validar tipo de archivo
    String contentType = detectContentType(file);
    List<String> allowedTypes = Arrays.asList(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "application/xml",
        "text/plain"
    );
    
    if (!allowedTypes.contains(contentType)) {
        throw new ValidationException("Tipo de archivo no permitido: " + contentType);
    }
    
    // Validar nombre de archivo
    if (file.getName().contains("..") || file.getName().contains("/")) {
        throw new ValidationException("Nombre de archivo inválido");
    }
}
```

## Costos Estimados

### Cálculo de Costos

**Supuestos**:
- 1,000 correos con adjuntos por día
- Tamaño promedio de adjunto: 500 KB
- Retención: 7 días
- Región: us-east-1

**Costos**:
- **Almacenamiento**: 3.5 GB promedio × $0.023/GB = $0.08/mes
- **PUT requests**: 1,000/día × 30 días × $0.005/1000 = $0.15/mes
- **GET requests**: 1,000/día × 30 días × $0.0004/1000 = $0.01/mes
- **DELETE requests**: 1,000/día × 30 días × $0.0004/1000 = $0.01/mes
- **Transferencia de datos**: Gratis (dentro de AWS)

**Total estimado**: ~$0.25/mes

## Referencias

- [AWS S3 Documentation](https://docs.aws.amazon.com/s3/)
- [S3 Lifecycle Configuration](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html)
- [S3 Security Best Practices](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html)
- [AWS SDK for Java 2.x - S3](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html)
- [S3 Pricing](https://aws.amazon.com/s3/pricing/)

## Soporte

Para problemas o preguntas, contactar al equipo de desarrollo de MSCorreos.
