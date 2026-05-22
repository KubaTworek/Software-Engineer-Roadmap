# syntax=docker/dockerfile:1.7

ARG MAVEN_VERSION=3.9.9
ARG ECLIPSE_TEMURIN_VERSION=21

FROM maven:${MAVEN_VERSION}-eclipse-temurin-${ECLIPSE_TEMURIN_VERSION} AS build
WORKDIR /app

COPY pom.xml ./

# Cache Maven dependencies separately from source code.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

FROM eclipse-temurin:${ECLIPSE_TEMURIN_VERSION}-jre-alpine AS runtime
WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=build /app/target/k8s-app-concept-java-1.0.0.jar /app/app.jar

RUN addgroup -S app && adduser -S app -G app
USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]