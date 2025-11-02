# Multi-stage build for course-service

# Stage 1: Build
FROM gradle:8.5-jdk21 AS build
WORKDIR /workspace

# Copy shared module first
COPY shared/java-common ./shared/java-common

# Copy Gradle files and source
COPY backend/course-service ./backend/course-service

# Build application
WORKDIR /workspace/backend/course-service
RUN gradle clean build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy JAR from build stage
COPY --from=build /workspace/backend/course-service/build/libs/*.jar app.jar

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=10s --timeout=5s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8082/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]