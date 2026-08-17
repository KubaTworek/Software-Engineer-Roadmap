FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY telemetry-agent/pom.xml telemetry-agent/pom.xml
COPY telemetry-server/pom.xml telemetry-server/pom.xml
COPY telemetry-agent telemetry-agent
RUN mvn -q -pl telemetry-agent package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/telemetry-agent/target/telemetry-agent-0.1.0.jar /app/agent.jar
ENTRYPOINT ["java", "-jar", "/app/agent.jar"]
