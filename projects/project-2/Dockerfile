FROM gradle:9.0.0-jdk21 AS build
WORKDIR /workspace

COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle clean bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S marketplace && adduser -S marketplace -G marketplace
COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN chown -R marketplace:marketplace /app

USER marketplace
EXPOSE 8080

STOPSIGNAL SIGTERM

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep '"status"' || exit 1

# Exec form sprawia, że JVM jest PID 1 i bez pośrednika otrzymuje SIGTERM.
# Opcje są jawne; gdy potrzebna jest dynamiczna konfiguracja, użyj JAVA_TOOL_OPTIONS.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
