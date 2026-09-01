package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict SSRF boundary for server-side HTTP clients. */
public final class SafeOutboundRequestPolicy {

    private static final Pattern IP_LITERAL = Pattern.compile("^[0-9.]+$|^\\[?[0-9a-fA-F:]+]?$" );
    private final Set<String> allowedHosts;

    public SafeOutboundRequestPolicy(Set<String> allowedHosts) {
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("at least one exact host is required");
        }
        this.allowedHosts = allowedHosts.stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public URI validate(URI uri) {
        Objects.requireNonNull(uri, "uri must not be null");
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw rejected("only HTTPS is allowed");
        if (uri.getUserInfo() != null) throw rejected("userinfo is forbidden");
        if (uri.getFragment() != null) throw rejected("fragments are forbidden");
        if (uri.getPort() != -1 && uri.getPort() != 443) throw rejected("only the default HTTPS port is allowed");

        String host = uri.getHost();
        if (host == null) throw rejected("URI must contain a valid host");
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (IP_LITERAL.matcher(normalizedHost).matches()) throw rejected("IP literals are forbidden");
        if (!allowedHosts.contains(normalizedHost)) throw rejected("host is not on the exact allowlist");
        return uri.normalize();
    }

    /** Redirect targets must pass the same policy; clients must not follow redirects automatically. */
    public URI validateRedirect(URI location) {
        return validate(location);
    }

    private static OutboundRequestRejectedException rejected(String reason) {
        return new OutboundRequestRejectedException(reason);
    }

    public static final class OutboundRequestRejectedException extends RuntimeException {
        public OutboundRequestRejectedException(String message) {
            super(message);
        }
    }
}
