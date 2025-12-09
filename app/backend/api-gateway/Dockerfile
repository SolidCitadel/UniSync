# Multi-stage build for API Gateway
# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder

WORKDIR /workspace

# Optimization: Cache dependencies
COPY backend/api-gateway/build.gradle.kts backend/api-gateway/settings.gradle.kts ./backend/api-gateway/
WORKDIR /workspace/backend/api-gateway
RUN gradle dependencies --no-daemon

# Copy Rest of Source
WORKDIR /workspace
COPY shared/java-common ./shared/java-common
COPY backend/api-gateway ./backend/api-gateway

# Build
WORKDIR /workspace/backend/api-gateway
RUN gradle bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy JAR from builder
COPY --from=builder /workspace/backend/api-gateway/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]