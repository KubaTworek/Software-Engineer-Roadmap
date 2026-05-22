# syntax=docker/dockerfile:1.7

ARG MAVEN_VERSION=3.9.9
ARG JAVA_VERSION=21

FROM maven:${MAVEN_VERSION}-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /app

COPY pom.xml ./

# Cache Maven dependencies separately from source code to improve rebuild time.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

COPY src ./src

# Build the application artifact.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q clean package -DskipTests

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime
WORKDIR /app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

COPY --from=build /app/target/demo-api-1.0.0.jar /app/app.jar

# Run as a non-root user to reduce container privileges.
RUN addgroup -S app && adduser -S app -G app
USER app

EXPOSE 8080

# The shell form allows JAVA_OPTS expansion.
# For stricter production images, consider a direct entrypoint or distroless runtime.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]