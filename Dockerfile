FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
# Resolve dependencies to improve caching
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S fieldops && adduser -S -G fieldops fieldops \
    && mkdir /app/evidence && chown fieldops:fieldops /app/evidence
COPY --from=builder /app/target/*.jar app.jar
USER fieldops
ENV APP_EVIDENCE_STORAGE_DIR=/app/evidence
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
