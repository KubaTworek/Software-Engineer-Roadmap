package com.example.observability.server.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/** Fail-closed SSRF policy: HTTPS plus an explicit hostname allow-list. */
@Component
public class WebhookTargetPolicy {
    private final Set<String> allowedHosts;

    public WebhookTargetPolicy(@Value("${telemetry.alerting.webhook-allowed-hosts:}") String hosts) {
        this.allowedHosts = Arrays.stream(hosts.split(",")).map(String::trim)
                .filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    URI validate(String target) {
        try {
            URI uri = URI.create(target);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !allowedHosts.contains(uri.getHost())) {
                throw new IllegalArgumentException("webhook must use HTTPS and an allowed host");
            }
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                    throw new IllegalArgumentException("webhook resolves to a private address");
                }
            }
            return uri;
        } catch (Exception exception) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY,
                    "unsafe webhook target", exception);
        }
    }
}
