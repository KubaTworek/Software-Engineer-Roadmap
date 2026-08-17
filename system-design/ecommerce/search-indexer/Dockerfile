FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY search-indexer/pom.xml ./pom.xml
COPY search-indexer/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/search-indexer-0.3.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
