# Docker build fix

## Problem

The previous Dockerfile copied only the selected service POM and `common/pom.xml` before running Maven:

```dockerfile
COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY payment-mock-service/pom.xml payment-mock-service/pom.xml
RUN mvn -pl payment-mock-service -am dependency:go-offline -DskipTests
```

That is not enough in a Maven multi-module reactor. The root `pom.xml` declares all modules, so Maven validates that every declared child module exists, even when `-pl` selects only one service.

Typical error:

```text
Child module /workspace/api-gateway of /workspace/pom.xml does not exist
Child module /workspace/catalog-service of /workspace/pom.xml does not exist
...
```

## Fix

Each service Dockerfile now copies the root POM and **all module POMs** before `dependency:go-offline`:

```dockerfile
COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY catalog-service/pom.xml catalog-service/pom.xml
COPY reservation-service/pom.xml reservation-service/pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY payment-mock-service/pom.xml payment-mock-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
```

Then it copies only `common` and the selected service source code before packaging:

```dockerfile
COPY common common
COPY payment-mock-service payment-mock-service
RUN mvn -pl payment-mock-service -am package -DskipTests
```

This keeps Docker layer caching useful while satisfying Maven reactor validation.

## Command

From the project root:

```bash
docker compose --profile apps up --build
```
