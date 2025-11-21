# Multi-stage build for schedule-service

# Stage 1: Build
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy shared module first
COPY shared/java-common ./shared/java-common

# Copy Gradle files and source
COPY backend/schedule-service ./backend/schedule-service

# Build application
WORKDIR /workspace/backend/schedule-service
RUN gradle clean build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /workspace/backend/schedule-service/build/libs/*.jar app.jar

# Expose port
EXPOSE 8083

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
