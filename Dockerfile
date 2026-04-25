# ============================================================
# Etapa 1: Build
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copiar archivos de Maven primero para aprovechar cache de capas
COPY pom.xml .
COPY .mvn/ .mvn/
# Descargar dependencias (se cachea si pom.xml no cambia)
RUN apk add --no-cache maven && mvn dependency:go-offline -B

# Copiar código fuente y compilar
COPY src/ src/
RUN mvn clean package -DskipTests -B

# ============================================================
# Etapa 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuario no-root por seguridad
RUN addgroup -S mscorreos && adduser -S mscorreos -G mscorreos

# Copiar JAR desde etapa de build
COPY --from=build /app/target/MSCorreos-0.0.1.jar app.jar

# Cambiar propietario
RUN chown mscorreos:mscorreos app.jar

USER mscorreos

# Puerto de la aplicación
EXPOSE 8080

# Health check para Docker/ECS
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

# Opciones JVM optimizadas para contenedores Java 21
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
