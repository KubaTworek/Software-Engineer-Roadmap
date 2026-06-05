FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY telemetry-server/pom.xml telemetry-server/pom.xml
COPY telemetry-agent/pom.xml telemetry-agent/pom.xml
COPY telemetry-server telemetry-server
RUN mvn -q -pl telemetry-server -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/telemetry-server/target/telemetry-server-0.1.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
