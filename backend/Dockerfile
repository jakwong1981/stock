# syntax=docker/dockerfile:1
# ---- Build stage ----
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
# Tests run in CI / scripts/build.sh (they need Docker for Testcontainers)
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package \
    && cp target/cyberstock-backend-*.jar /app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app && apk add --no-cache wget
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
