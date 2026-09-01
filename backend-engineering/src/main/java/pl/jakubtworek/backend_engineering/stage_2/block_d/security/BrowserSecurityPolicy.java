package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.util.LinkedHashMap;
import java.util.Map;

/** Separates HTTPS, CORS and CSRF decisions instead of treating them as synonyms. */
public final class BrowserSecurityPolicy {

    public Decision authorize(RequestContext request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (!request.https()) return Decision.deny("HTTPS is required");
        if (request.crossOriginBrowserRequest() && !request.originAllowed()) {
            return Decision.deny("browser origin is not allowed by CORS policy");
        }
        if (request.credentialTransport() == CredentialTransport.COOKIE
                && isUnsafe(request.method())
                && !request.csrfTokenValid()) {
            return Decision.deny("cookie-authenticated unsafe request requires a valid CSRF token");
        }
        return Decision.allow();
    }

    public Map<String, String> responseHeaders(boolean sensitiveResponse) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        headers.put("Referrer-Policy", "no-referrer");
        if (sensitiveResponse) headers.put("Cache-Control", "no-store");
        return Map.copyOf(headers);
    }

    private static boolean isUnsafe(String method) {
        return !SetLike.SAFE_METHODS.contains(method == null ? "" : method.toUpperCase());
    }

    private static final class SetLike {
        private static final java.util.Set<String> SAFE_METHODS = java.util.Set.of("GET", "HEAD", "OPTIONS");
    }

    public enum CredentialTransport {
        AUTHORIZATION_HEADER,
        COOKIE
    }

    public record RequestContext(
            String method,
            boolean https,
            boolean crossOriginBrowserRequest,
            boolean originAllowed,
            CredentialTransport credentialTransport,
            boolean csrfTokenValid) {

        public RequestContext {
            if (method == null || method.isBlank()) throw new IllegalArgumentException("method is required");
            if (credentialTransport == null) throw new IllegalArgumentException("credentialTransport is required");
        }
    }

    public record Decision(boolean allowed, String reason) {
        public static Decision allow() {
            return new Decision(true, "allowed");
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }
}
