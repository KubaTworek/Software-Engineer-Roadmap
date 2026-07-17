FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY notification-service/pom.xml ./pom.xml
COPY notification-service/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/notification-service-0.3.0.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
