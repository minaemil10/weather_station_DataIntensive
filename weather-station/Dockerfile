# Build stage for weather-station
FROM maven:3.9.8-eclipse-temurin-11 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests dependency:copy-dependencies package

# Runtime stage
FROM eclipse-temurin:11-jre-jammy
WORKDIR /app
COPY --from=builder /app/target/classes /app/classes
COPY --from=builder /app/target/dependency /app/dependency
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "/app/classes:/app/dependency/*", "com.weather.Main"]
