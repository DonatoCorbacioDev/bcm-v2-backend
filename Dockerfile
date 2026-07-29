# Multi-stage build for Spring Boot application
# Stage 1: Build with Maven
FROM maven:3.9.15-eclipse-temurin-26-alpine AS builder

WORKDIR /app

# Copy pom.xml and download dependencies (caching layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster build)
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime with JRE
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Tesseract OCR engine, used as a fallback for scanned PDFs with no text layer
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-ita

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# /app/uploads is normally a named volume (contract documents, invoices -
# see UPLOAD_DIR). Docker seeds a fresh volume's ownership from whatever
# exists at this path in the image at mount time, so it must already be
# owned by the runtime user - otherwise the volume comes up root-owned and
# every file upload fails with AccessDeniedException.
RUN mkdir -p /app/uploads && chown -R spring:spring /app/uploads

USER spring:spring

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose application port
EXPOSE 8090

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8090/api/v1/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]