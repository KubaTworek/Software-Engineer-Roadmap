package com.example.ratelimiter.security;

import com.example.ratelimiter.config.RateLimiterProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves the first untrusted hop without trusting caller-supplied prefixes. */
@Component
public class ClientIpResolver {
    private static final int MAX_FORWARDED_LENGTH = 4096;
    private final List<Subnet> trusted;

    public ClientIpResolver(RateLimiterProperties properties) {
        this.trusted = properties.getSecurity().getTrustedProxies().stream().map(Subnet::parse).toList();
    }

    public String resolve(HttpServletRequest request) {
        String remote = normalize(request.getRemoteAddr());
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!isTrusted(remote) || forwarded == null || forwarded.isBlank()
                || forwarded.length() > MAX_FORWARDED_LENGTH) return remote;

        List<String> hops = new ArrayList<>();
        for (String raw : forwarded.split(",")) {
            String hop = normalize(raw);
            if (hop != null) hops.add(hop);
        }
        // Walk from the peer towards the client. Walking left-to-right lets an
        // attacker prepend a fake address before the real untrusted hop.
        for (int i = hops.size() - 1; i >= 0; i--) {
            if (!isTrusted(hops.get(i))) return hops.get(i);
        }
        return remote;
    }

    public boolean isTrustedPeer(HttpServletRequest request) {
        return isTrusted(normalize(request.getRemoteAddr()));
    }

    private boolean isTrusted(String ip) {
        if (ip == null) return false;
        return trusted.stream().anyMatch(subnet -> subnet.contains(ip));
    }

    private String normalize(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.startsWith("[") && value.contains("]")) value = value.substring(1, value.indexOf(']'));
        if (!value.matches("[0-9A-Fa-f:.]+")) return null;
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private record Subnet(byte[] network, int prefix) {
        static Subnet parse(String cidr) {
            try {
                String[] parts = cidr.trim().split("/", 2);
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int bits = parts.length == 2 ? Integer.parseInt(parts[1]) : address.length * 8;
                if (bits < 0 || bits > address.length * 8) throw new IllegalArgumentException("Invalid prefix");
                return new Subnet(address, bits);
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr, exception);
            }
        }

        boolean contains(String candidate) {
            try {
                byte[] address = InetAddress.getByName(candidate).getAddress();
                if (address.length != network.length) return false;
                int full = prefix / 8;
                int rest = prefix % 8;
                for (int i = 0; i < full; i++) if (address[i] != network[i]) return false;
                if (rest == 0) return true;
                int mask = 0xff << (8 - rest);
                return (address[full] & mask) == (network[full] & mask);
            } catch (UnknownHostException exception) {
                return false;
            }
        }
    }
}
