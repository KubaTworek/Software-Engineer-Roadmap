# POST JSON proxy fix

## Problem

`POST /reservations` returned `500` from `reservation-service` during k6 tests. The API Gateway proxied the JSON body as a plain string but did not preserve/set `Content-Type: application/json` on downstream POST requests.

Spring MVC in downstream services could then reject or fail to read the request body. The global error handler also mapped some request parsing errors to generic `500`, which made the failure harder to diagnose.

## Fix

- `GatewayProxyController` now sets downstream POST `Content-Type` based on the incoming request, defaulting to `application/json`.
- `GlobalExceptionHandler` now handles malformed JSON and unsupported media type as `400`/`415`.
- `GlobalExceptionHandler` logs stack traces for unexpected failures.
- k6 summary now reads metrics from `data.metrics.*.values`, which is the actual k6 summary JSON shape.
- k6 reservation check no longer treats non-200 responses as passing the `has id` check.
