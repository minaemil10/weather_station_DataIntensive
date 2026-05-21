# Build stage for rain-processor
FROM maven:3.9.8-eclipse-temurin-8 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests dependency:copy-dependencies package

# Runtime stage
FROM eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/classes /app/classes
COPY --from=builder /app/target/dependency /app/dependency
ENTRYPOINT ["java", "-cp", "/app/classes:/app/dependency/*", "com.example.RainProcessor"]
