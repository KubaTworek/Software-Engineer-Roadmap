FROM eclipse-temurin:21-jre-alpine
ARG JAR_FILE=target/order-service-0.1.0-SNAPSHOT.jar
WORKDIR /app
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
