FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN ./mvnw --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home appuser
COPY --from=build --chown=appuser:appuser /app/target/video-streaming-0.5.0.jar app.jar
RUN mkdir -p /app/var/transcoding /app/var/live && chown -R appuser:appuser /app/var
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD curl -fsS "http://localhost:${SERVER_PORT:-8080}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
