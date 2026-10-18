package com.example.observability.server.notification;

import com.example.observability.server.metadata.MetadataStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Trwały outbox oddziela zmianę stanu alertu od zawodnego efektu sieciowego. */
@Component
public class NotificationDeliveryWorker {
    private final MetadataStore metadata;
    private final ObjectMapper json;
    private final Map<String, NotificationChannel> channels;

    public NotificationDeliveryWorker(MetadataStore metadata, ObjectMapper json,
                                      List<NotificationChannel> channels) {
        this.metadata = metadata;
        this.json = json;
        this.channels = channels.stream().collect(Collectors.toUnmodifiableMap(
                NotificationChannel::type, Function.identity()));
    }

    @Scheduled(fixedDelayString = "${telemetry.alerting.delivery-interval-ms:1000}")
    public void deliver() {
        for (var row : metadata.claimNotifications(100)) {
            try {
                var channel = channels.get(row.channel());
                if (channel == null) throw new IllegalArgumentException("unknown channel: " + row.channel());
                var envelope = json.readValue(row.payload(), AlertRouter.NotificationEnvelope.class);
                channel.notify(envelope.rule(), envelope.event(), envelope.target());
                metadata.notificationDelivered(row.id());
            } catch (Exception exception) {
                metadata.notificationFailed(row.id(), row.attempts(), exception.getMessage());
            }
        }
    }
}
