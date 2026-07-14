#improvised dockerfile
FROM maven:3.8.3-openjdk-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:resolve
COPY . .
RUN mvn clean install -DskipTests=true

#--------------------------------------
# Stage 2 - app build
#--------------------------------------

# Use Eclipse Temurin (actively maintained, replaces deprecated openjdk)
FROM eclipse-temurin:17-jre-alpine

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app 

# Copy built jar from stage 1
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

# Run as non-root user
USER appuser

# Expose application port 
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]