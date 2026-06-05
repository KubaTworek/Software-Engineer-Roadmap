package com.example.urlshortener.validation;

import com.example.urlshortener.exception.InvalidUrlException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class UrlValidator {

    public URI validatePublicHttpUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        String scheme = uri.getScheme();
        if (scheme == null) throw new InvalidUrlException("URL must include a scheme: http or https");
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
            throw new InvalidUrlException("Only http and https URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new InvalidUrlException("URL must include a valid host");
        String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        if (isLocalhost(asciiHost)) throw new InvalidUrlException("Localhost URLs are not allowed");
        if (isPrivateOrLoopbackAddress(asciiHost)) {
            throw new InvalidUrlException("Private, loopback, link-local and multicast addresses are not allowed");
        }
        if (uri.getRawUserInfo() != null) throw new InvalidUrlException("URLs with user info are not allowed");
        return uri;
    }

    private URI parse(String rawUrl) {
        try {
            return new URI(rawUrl).normalize();
        } catch (URISyntaxException exception) {
            throw new InvalidUrlException("URL is not syntactically valid");
        }
    }

    private boolean isLocalhost(String host) { return host.equals("localhost") || host.endsWith(".localhost"); }

    private boolean isPrivateOrLoopbackAddress(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
        } catch (Exception ignored) {
            return false;
        }
    }
}
