# ==========================================
# Stage 1: Build stage
# ==========================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Cache Maven dependencies by copying pom.xml first
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build the production artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# Stage 2: Runtime stage
# ==========================================
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-root user for security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the generated fat jar from build stage
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

# Render dynamically assigns a port via the PORT environment variable.
# Fallback to 8082 for local development.
ENV PORT=8082

# JVM Memory optimization for Render Free/Starter tiers (512MB RAM)
# Using 75% max RAM percentage leaves ~128MB for metaspace, thread stacks, and OS.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080 10000

# Container healthcheck: tests dynamic PORT so it works both locally and on Render
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- "http://localhost:${PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

# Launch Spring Boot with dynamic port mapping and JVM tuning
# Using exec ensures Java is PID 1 to gracefully handle SIGTERM from Render
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT} -jar app.jar"]
