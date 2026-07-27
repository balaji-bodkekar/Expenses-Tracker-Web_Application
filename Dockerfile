# Stage 1 - Build Stage
FROM maven:3.8.3-openjdk-17 AS builder

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:resolve

COPY . .
RUN mvn clean install -DskipTests=true

# Stage 2 - Runtime Stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 1. Download OpenTelemetry Java Agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# 2. Copy built jar from stage 1
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar
RUN chown appuser:appgroup /app/opentelemetry-javaagent.jar

# 3. Set Default OpenTelemetry Config (Can be overridden at runtime)
ENV OTEL_SERVICE_NAME="expenses-tracker" \
    OTEL_TRACES_EXPORTER="otlp" \
    OTEL_METRICS_EXPORTER="otlp" \
    OTEL_LOGS_EXPORTER="otlp" \
    OTEL_EXPORTER_OTLP_ENDPOINT="http://otel-collector:4317"

# Run as non-root user
USER appuser

# Expose application port
EXPOSE 8080

# 4. Launch with OpenTelemetry Agent Attached
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "app.jar"]