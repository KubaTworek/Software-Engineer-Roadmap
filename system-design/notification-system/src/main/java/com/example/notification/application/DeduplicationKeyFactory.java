package com.example.notification.application;

import com.example.notification.domain.Channel;
import com.example.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class DeduplicationKeyFactory {
    public String create(String tenantId, String userId, NotificationType type, List<Channel> channels, Map<String, Object> payload) {
        String channelPart = channels.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        String payloadPart = new TreeMap<>(payload).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("|"));
        String canonical = tenantId + ":" + userId + ":" + type.name() + ":" + channelPart + ":" + payloadPart;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot build request fingerprint", ex);
        }
    }
}
