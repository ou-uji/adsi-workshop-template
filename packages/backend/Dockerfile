# Multi-stage Dockerfile for Spring Boot backend
# CRITICAL: This file is used by CDK with --network=sagemaker (SageMaker constraint)

# ========================================
# Stage 1: Build
# ========================================
FROM gradle:8.14.5-jdk21-alpine AS build

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradle/ gradle/
COPY gradlew gradlew.bat settings.gradle build.gradle ./

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src/ src/

# Build JAR (skip tests for faster build; tests run in CI)
RUN ./gradlew bootJar --no-daemon -x test

# ========================================
# Stage 2: Runtime
# ========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Install curl for health check
RUN apk add --no-cache curl

# Expose backend port
EXPOSE 8080

# Set Spring profile (default: workshop with H2 in-memory)
ENV SPRING_PROFILES_ACTIVE=workshop

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Run the application
CMD ["java", "-jar", "app.jar"]
