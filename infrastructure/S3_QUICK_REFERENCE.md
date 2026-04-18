# Amazon S3 - Referencia Rápida

## Despliegue Rápido

```bash
cd infrastructure/scripts
chmod +x deploy-s3.sh
./deploy-s3.sh dev
```

## Información del Bucket

| Propiedad | Valor |
|-----------|-------|
| Nombre | `mscorreos-adjuntos-{env}` |
| Región | us-east-1 |
| Versionamiento | Habilitado |
| Cifrado | AES-256 |
| Ciclo de vida | 7 días |
| Acceso público | Bloqueado |

## Comandos Útiles

### Verificar Configuración

```bash
# Obtener nombre del bucket
aws cloudformation describe-stacks \
  --stack-name mscorreos-s3-dev \
  --query 'Stacks[0].Outputs[?OutputKey==`BucketName`].OutputValue' \
  --output text

# Ver política de ciclo de vida
aws s3api get-bucket-lifecycle-configuration \
  --bucket mscorreos-adjuntos-dev

# Ver política del bucket
aws s3api get-bucket-policy \
  --bucket mscorreos-adjuntos-dev

# Ver configuración de cifrado
aws s3api get-bucket-encryption \
  --bucket mscorreos-adjuntos-dev
```

### Operaciones Básicas

```bash
# Subir archivo
aws s3 cp archivo.pdf s3://mscorreos-adjuntos-dev/test/

# Descargar archivo
aws s3 cp s3://mscorreos-adjuntos-dev/test/archivo.pdf ./

# Listar objetos
aws s3 ls s3://mscorreos-adjuntos-dev/ --recursive

# Eliminar objeto
aws s3 rm s3://mscorreos-adjuntos-dev/test/archivo.pdf

# Ver tamaño del bucket
aws s3 ls s3://mscorreos-adjuntos-dev --recursive --summarize
```

### Monitoreo

```bash
# Tamaño del bucket (CloudWatch)
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

## Código de Ejemplo

### Subir Adjunto (Productor)

```java
// ShrimpSoftServer
S3Client s3Client = S3Client.create();
String bucket = "mscorreos-adjuntos-dev";
String key = "adjuntos/" + UUID.randomUUID() + "/" + fileName;

PutObjectRequest request = PutObjectRequest.builder()
    .bucket(bucket)
    .key(key)
    .contentType(contentType)
    .build();

s3Client.putObject(request, RequestBody.fromFile(file));
```

### Descargar Adjunto (Consumidor)

```java
// MSCorreos
GetObjectRequest request = GetObjectRequest.builder()
    .bucket(bucket)
    .key(key)
    .build();

ResponseBytes<?> objectBytes = s3Client.getObjectAsBytes(request);
File tempFile = File.createTempFile("adjunto-", ".tmp");
Files.write(tempFile.toPath(), objectBytes.asByteArray());
```

### Eliminar Adjunto (Consumidor)

```java
// MSCorreos - después de envío exitoso
DeleteObjectRequest request = DeleteObjectRequest.builder()
    .bucket(bucket)
    .key(key)
    .build();

s3Client.deleteObject(request);
```

## Configuración

### application.yml

```yaml
aws:
  region: us-east-1
  s3:
    bucket: ${S3_BUCKET_ADJUNTOS:mscorreos-adjuntos-dev}
    max-attachment-size: 10485760  # 10 MB
```

### Variables de Entorno

```bash
export AWS_REGION=us-east-1
export S3_BUCKET_ADJUNTOS=mscorreos-adjuntos-dev
```

## Políticas IAM

### MSCorreos (Consumidor)

```json
{
  "Effect": "Allow",
  "Action": ["s3:GetObject", "s3:DeleteObject", "s3:ListBucket"],
  "Resource": [
    "arn:aws:s3:::mscorreos-adjuntos-*/*",
    "arn:aws:s3:::mscorreos-adjuntos-*"
  ]
}
```

### ShrimpSoftServer (Productor)

```json
{
  "Effect": "Allow",
  "Action": ["s3:PutObject", "s3:PutObjectAcl", "s3:ListBucket"],
  "Resource": [
    "arn:aws:s3:::mscorreos-adjuntos-*/*",
    "arn:aws:s3:::mscorreos-adjuntos-*"
  ]
}
```

## Troubleshooting

| Problema | Solución |
|----------|----------|
| Access Denied | Verificar políticas IAM y del bucket |
| Bucket Already Exists | Cambiar nombre del bucket (globalmente único) |
| Archivos no se eliminan | Verificar política de ciclo de vida, esperar 24h |
| Bucket lleno | Revisar archivos antiguos, reducir retención |

## Alarmas CloudWatch

| Alarma | Umbral | Acción |
|--------|--------|--------|
| Tamaño del bucket | 10 GB | Revisar archivos antiguos |
| Número de objetos | 10,000 | Verificar eliminación automática |

## Costos Estimados

- **Almacenamiento**: ~$0.08/mes (3.5 GB promedio)
- **Requests**: ~$0.17/mes (1,000 correos/día)
- **Total**: ~$0.25/mes

## Enlaces Útiles

- [Guía Completa](./S3_CONFIGURATION_GUIDE.md)
- [AWS S3 Documentation](https://docs.aws.amazon.com/s3/)
- [S3 Pricing](https://aws.amazon.com/s3/pricing/)
