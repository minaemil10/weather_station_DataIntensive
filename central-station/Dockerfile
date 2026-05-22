# Build stage for central-station
FROM maven:3.9.8-eclipse-temurin-11 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests clean package -DskipTests

# Runtime stage - Kubernetes-friendly
FROM eclipse-temurin:11-jre-jammy

# Install curl for health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN useradd -m -u 1000 appuser

# Set working directory
WORKDIR /app

# Copy the fat JAR from builder
COPY --from=builder /app/target/central-station.jar /app/central-station.jar

# Create persistent volume mount point
RUN mkdir -p /data/bitcask && chown -R appuser:appuser /app /data

# Switch to non-root user
USER appuser

# Environment variables with sensible defaults
ENV DATA_DIR=/data/bitcask \
    KAFKA_BROKERS=kafka:9092 \
    SERVER_PORT=8080 \
    JAVA_OPTS="-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewCollectionPercentage=30 -XX:G1MaxNewCollectionLength=800m -XX:+ParallelRefProcEnabled"

# Expose API port
EXPOSE 8080

# Health check for Kubernetes liveness/readiness probes
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java"]
CMD ["-jar", "-Xmx512m", "/app/central-station.jar"]
