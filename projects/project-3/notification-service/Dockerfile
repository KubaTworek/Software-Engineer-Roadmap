FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the reactor POM and all child module POMs first.
# Maven validates every module declared in the root pom.xml, even when building with -pl.
COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY reservation-service/pom.xml reservation-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY payment-mock-service/pom.xml payment-mock-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml

RUN mvn -pl notification-service -am dependency:go-offline -DskipTests

# Copy only the modules needed to build this service and its shared dependency.
COPY common common
COPY notification-service notification-service

RUN mvn -pl notification-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/notification-service/target/notification-service-*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
