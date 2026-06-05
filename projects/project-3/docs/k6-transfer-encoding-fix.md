# k6 Transfer-Encoding fix

## Problem

During k6 tests the gateway returned responses rejected by Go HTTP client:

```text
net/http: HTTP/1.x transport connection broken: too many transfer encodings: ["chunked" "chunked"]
```

## Cause

`api-gateway` proxied downstream responses using `WebClient.toEntity(String.class)`. That copied hop-by-hop HTTP headers from downstream services, including `Transfer-Encoding: chunked`.

The servlet container then added its own transfer encoding, so k6 received duplicated `Transfer-Encoding` headers.

## Fix

`GatewayProxyController` now uses `exchangeToMono(...)` and builds a sanitized `ResponseEntity` manually. The gateway no longer forwards hop-by-hop response headers:

- `Connection`
- `Keep-Alive`
- `Proxy-Authenticate`
- `Proxy-Authorization`
- `TE`
- `Trailer`
- `Transfer-Encoding`
- `Upgrade`
- `Content-Length`

This makes the proxy behave correctly for k6 and other strict HTTP clients.
