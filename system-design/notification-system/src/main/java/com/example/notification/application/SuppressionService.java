package com.example.notification.application;

import com.example.notification.domain.Channel;
import com.example.notification.domain.SuppressionEntry;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;

@Service
public class SuppressionService {
    private final Ports.SuppressionRepository repository;
    private final Clock clock;
    public SuppressionService(Ports.SuppressionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public boolean isSuppressed(String tenantId, Channel channel, String contact) {
        return contact != null && repository.exists(tenantId, channel, hash(contact));
    }
    public void suppress(String tenantId, Channel channel, String contact, String reason) {
        if (contact != null) repository.save(new SuppressionEntry(
                tenantId, channel, hash(contact), reason, clock.instant()));
    }
    public void forget(String tenantId, Channel channel, String contact) {
        if (contact != null) repository.delete(tenantId, channel, hash(contact));
    }
    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
