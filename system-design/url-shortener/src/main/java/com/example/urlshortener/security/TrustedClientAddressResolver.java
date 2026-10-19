package com.example.urlshortener.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves the client address without trusting caller-controlled forwarding headers.
 * The chain is read from the nearest hop backwards and stops at the first untrusted hop.
 */
@Component
public class TrustedClientAddressResolver {
    private final List<Cidr> trusted;

    public TrustedClientAddressResolver(TrustedProxyProperties properties) {
        trusted = properties.getCidrs().stream().map(Cidr::parse).toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = canonical(request.getRemoteAddr());
        if (!isTrusted(remote)) return remote;

        List<String> chain = forwardedChain(request.getHeader("X-Forwarded-For"));
        for (int index = chain.size() - 1; index >= 0; index--) {
            String hop = canonical(chain.get(index));
            if (!isTrusted(hop)) return hop;
        }

        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? remote : canonical(realIp);
    }

    private List<String> forwardedChain(String header) {
        if (header == null || header.isBlank() || header.length() > 2048) return List.of();
        var result = new ArrayList<String>();
        for (String value : header.split(",")) {
            if (!value.isBlank()) result.add(value.trim());
        }
        return result;
    }

    private boolean isTrusted(String address) {
        return trusted.stream().anyMatch(cidr -> cidr.contains(address));
    }

    private String canonical(String value) {
        try {
            return InetAddress.getByName(value == null ? "" : value.trim()).getHostAddress();
        } catch (UnknownHostException exception) {
            return "invalid-address";
        }
    }

    private record Cidr(byte[] network, int prefix) {
        static Cidr parse(String text) {
            try {
                String[] parts = text.trim().split("/", 2);
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : address.length * 8;
                if (prefix < 0 || prefix > address.length * 8) throw new IllegalArgumentException("Invalid CIDR: " + text);
                return new Cidr(address, prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + text, exception);
            }
        }

        boolean contains(String candidate) {
            try {
                byte[] address = InetAddress.getByName(candidate).getAddress();
                if (address.length != network.length) return false;
                int fullBytes = prefix / 8;
                for (int i = 0; i < fullBytes; i++) if (address[i] != network[i]) return false;
                int remaining = prefix % 8;
                if (remaining == 0) return true;
                int mask = 0xff << (8 - remaining);
                return (address[fullBytes] & mask) == (network[fullBytes] & mask);
            } catch (UnknownHostException exception) {
                return false;
            }
        }
    }
}
