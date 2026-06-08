package com.example.notification.application;

import com.example.notification.domain.Channel;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ProviderRegistry {
    private final Map<Channel, List<Ports.NotificationProvider>> providers = new EnumMap<>(Channel.class);

    public ProviderRegistry(List<Ports.NotificationProvider> providerList) {
        providerList.sort(AnnotationAwareOrderComparator.INSTANCE);
        for (Ports.NotificationProvider provider : providerList) {
            providers.computeIfAbsent(provider.channel(), ignored -> new java.util.ArrayList<>()).add(provider);
        }
    }

    public List<Ports.NotificationProvider> providersFor(Channel channel) {
        List<Ports.NotificationProvider> result = providers.get(channel);
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("No providers registered for channel: " + channel);
        }
        return List.copyOf(result);
    }
}
