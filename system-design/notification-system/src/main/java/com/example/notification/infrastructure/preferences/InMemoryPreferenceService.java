package com.example.notification.infrastructure.preferences;

import com.example.notification.application.AuditService;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryPreferenceService implements Ports.PreferenceService {
    private final Map<String, Map<NotificationType, Map<Channel, Boolean>>> preferences = new ConcurrentHashMap<>();
    private final AuditService auditService;

    public InMemoryPreferenceService(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public List<Channel> resolveChannels(String tenantId, String userId, NotificationType type, List<Channel> requestedChannels) {
        List<Channel> base = requestedChannels == null || requestedChannels.isEmpty() ? defaultChannels(type) : requestedChannels;
        Map<Channel, Boolean> typePrefs = preferences.getOrDefault(key(tenantId, userId), defaults()).getOrDefault(type, Map.of());

        List<Channel> selected = new ArrayList<>();
        for (Channel channel : base) {
            boolean enabled = typePrefs.getOrDefault(channel, defaultEnabled(type, channel));
            if (enabled || mandatory(type)) selected.add(channel);
        }
        return selected.stream().distinct().toList();
    }

    @Override
    public Map<NotificationType, Map<Channel, Boolean>> getPreferences(String tenantId, String userId) {
        return copy(preferences.getOrDefault(key(tenantId, userId), defaults()));
    }

    @Override
    public Map<NotificationType, Map<Channel, Boolean>> updatePreferences(String tenantId, String userId, Map<NotificationType, Map<Channel, Boolean>> prefs) {
        Map<NotificationType, Map<Channel, Boolean>> merged = defaults();
        for (var entry : prefs.entrySet()) {
            Map<Channel, Boolean> channels = new EnumMap<>(Channel.class);
            channels.putAll(merged.getOrDefault(entry.getKey(), Map.of()));
            channels.putAll(entry.getValue());
            merged.put(entry.getKey(), channels);
        }
        preferences.put(key(tenantId, userId), merged);
        auditService.record(tenantId, userId, AuditAction.PREFERENCES_UPDATED, null, Map.of("userId", userId));
        return copy(merged);
    }

    private String key(String tenantId, String userId) { return tenantId + ":" + userId; }

    private List<Channel> defaultChannels(NotificationType type) {
        return switch (type) {
            case PASSWORD_RESET -> List.of(Channel.EMAIL, Channel.SMS);
            case PAYMENT_FAILED -> List.of(Channel.EMAIL, Channel.PUSH, Channel.IN_APP);
            case SECURITY_ALERT -> List.of(Channel.EMAIL, Channel.SMS, Channel.PUSH, Channel.IN_APP);
            case MARKETING_PROMOTION -> List.of(Channel.EMAIL, Channel.PUSH, Channel.IN_APP);
            case WEEKLY_DIGEST -> List.of(Channel.EMAIL, Channel.IN_APP);
            case CAMPAIGN_MESSAGE -> List.of(Channel.EMAIL, Channel.PUSH);
        };
    }

    private boolean mandatory(NotificationType type) {
        return type == NotificationType.PASSWORD_RESET || type == NotificationType.SECURITY_ALERT;
    }

    private boolean defaultEnabled(NotificationType type, Channel channel) {
        return !(type == NotificationType.MARKETING_PROMOTION && channel == Channel.SMS);
    }

    private Map<NotificationType, Map<Channel, Boolean>> defaults() {
        Map<NotificationType, Map<Channel, Boolean>> result = new EnumMap<>(NotificationType.class);
        for (NotificationType type : NotificationType.values()) {
            Map<Channel, Boolean> channels = new EnumMap<>(Channel.class);
            for (Channel channel : Channel.values()) channels.put(channel, defaultEnabled(type, channel));
            result.put(type, channels);
        }
        return result;
    }

    private Map<NotificationType, Map<Channel, Boolean>> copy(Map<NotificationType, Map<Channel, Boolean>> source) {
        Map<NotificationType, Map<Channel, Boolean>> result = new EnumMap<>(NotificationType.class);
        for (var entry : source.entrySet()) {
            Map<Channel, Boolean> channels = new EnumMap<>(Channel.class);
            channels.putAll(entry.getValue());
            result.put(entry.getKey(), channels);
        }
        return result;
    }
}
