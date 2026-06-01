FROM gradle:8.10.2-jdk21 AS build
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

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep '"status"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
