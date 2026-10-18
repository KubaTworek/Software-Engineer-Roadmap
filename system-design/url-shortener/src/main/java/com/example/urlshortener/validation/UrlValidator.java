package com.example.urlshortener.validation;

import com.example.urlshortener.exception.InvalidUrlException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validates destinations accepted by the public shortener.
 *
 * <p>This boundary reduces phishing and accidental links to private networks; a
 * redirect response itself does not make a server-side request and is therefore
 * not an SSRF sink. If a future preview crawler or webhook starts fetching these
 * URLs, it must repeat DNS validation at connection time, pin the validated IP and
 * enforce egress policy to address DNS rebinding and TOCTOU attacks.</p>
 */
@Component
public class UrlValidator {

    public URI validatePublicHttpUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new InvalidUrlException("URL must include a scheme: http or https");
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
            throw new InvalidUrlException("Only http and https URLs are allowed");
        }
        if (uri.getRawUserInfo() != null) {
            throw new InvalidUrlException("URLs with user info are not allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must include a valid host");
        }

        final String asciiHost;
        try {
            asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException invalidIdn) {
            throw new InvalidUrlException("URL host is not valid");
        }
        if (isLocalhost(asciiHost)) {
            throw new InvalidUrlException("Localhost URLs are not allowed");
        }
        if (resolvesToNonPublicAddress(asciiHost)) {
            throw new InvalidUrlException(
                    "Private, loopback, link-local and multicast addresses are not allowed");
        }
        return uri;
    }

    private URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("URL must not be blank");
        }
        try {
            return new URI(rawUrl).normalize();
        } catch (URISyntaxException exception) {
            throw new InvalidUrlException("URL is not syntactically valid");
        }
    }

    private boolean isLocalhost(String host) {
        return host.equals("localhost") || host.endsWith(".localhost");
    }

    /**
     * Every DNS answer has to be public. Checking only the first answer makes the
     * result depend on resolver ordering. DNS lookup deliberately fails open here
     * because the value is only stored and returned in a redirect; a server-side
     * fetcher should instead fail closed.
     */
    private boolean resolvesToNonPublicAddress(String host) {
        try {
            return Arrays.stream(InetAddress.getAllByName(host)).anyMatch(address ->
                    address.isAnyLocalAddress()
                            || address.isLoopbackAddress()
                            || address.isLinkLocalAddress()
                            || address.isSiteLocalAddress()
                            || address.isMulticastAddress());
        } catch (Exception unresolved) {
            return false;
        }
    }
}
