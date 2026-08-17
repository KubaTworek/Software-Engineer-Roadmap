package com.example.notification.api.dto;

import com.example.notification.application.CreateNotificationResult;
import com.example.notification.domain.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {}

    public record ContactPointRequest(String email, String phoneNumber, String pushToken) {}

    public record CreateNotificationRequest(
            @NotBlank String userId,
            @NotNull NotificationType notificationType,
            List<Channel> channels,
            @NotNull ContactPointRequest contactPoint,
            @NotNull Map<String, Object> payload,
            String idempotencyKey,
            Instant expiresAt
    ) {}

    public record NotificationResponse(
            UUID id,
            String tenantId,
            String userId,
            NotificationType notificationType,
            List<Channel> requestedChannels,
            List<Channel> selectedChannels,
            NotificationStatus status,
            Map<String, Object> payload,
            String idempotencyKey,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt
    ) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getTenantId(), n.getUserId(), n.getNotificationType(),
                    n.getRequestedChannels(), n.getSelectedChannels(), n.getStatus(), n.getPayload(), n.getIdempotencyKey(),
                    n.getExpiresAt(), n.getCreatedAt(), n.getUpdatedAt(), n.getArchivedAt());
        }
    }

    public record CreateNotificationResponse(NotificationResponse notification, boolean duplicate, String duplicateReason) {
        public static CreateNotificationResponse from(CreateNotificationResult result) {
            return new CreateNotificationResponse(NotificationResponse.from(result.notification()), result.duplicate(), result.duplicateReason());
        }
    }

    public record NotificationJobResponse(
            UUID id,
            UUID notificationId,
            String tenantId,
            String userId,
            NotificationType notificationType,
            Channel channel,
            NotificationStatus status,
            int attemptCount,
            int maxAttempts,
            Instant nextAttemptAt,
            String providerName,
            String providerMessageId,
            boolean fallbackUsed,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static NotificationJobResponse from(NotificationJob job) {
            return new NotificationJobResponse(job.getId(), job.getNotificationId(), job.getTenantId(), job.getUserId(),
                    job.getNotificationType(), job.getChannel(), job.getStatus(), job.getAttemptCount(), job.getMaxAttempts(),
                    job.getNextAttemptAt(), job.getProviderName(), job.getProviderMessageId(), job.isFallbackUsed(),
                    job.getLastError(), job.getCreatedAt(), job.getUpdatedAt());
        }
    }

    public record TemplateRequest(
            @NotBlank String templateKey,
            @NotNull NotificationType notificationType,
            @NotNull Channel channel,
            @NotBlank String subject,
            @NotBlank String body,
            Set<String> requiredVariables
    ) {}

    public record TemplateResponse(
            UUID id,
            String tenantId,
            String templateKey,
            NotificationType notificationType,
            Channel channel,
            int version,
            String subject,
            String body,
            Set<String> requiredVariables,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static TemplateResponse from(NotificationTemplate t) {
            return new TemplateResponse(t.getId(), t.getTenantId(), t.getTemplateKey(), t.getNotificationType(),
                    t.getChannel(), t.getVersion(), t.getSubject(), t.getBody(), t.getRequiredVariables(),
                    t.isActive(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    public record UpdatePreferencesRequest(@NotNull Map<NotificationType, Map<Channel, Boolean>> preferences) {}

    public record ProviderWebhookRequest(@NotBlank String providerMessageId, @NotNull ProviderWebhookStatus status, String reason) {}

    public record CreateCampaignRequest(
            @NotBlank String name,
            @NotNull NotificationType notificationType,
            @NotEmpty List<String> userIds,
            @NotEmpty List<Channel> channels,
            @NotNull Map<String, Object> payload
    ) {}

    public record BufferDigestRequest(@NotBlank String userId, @NotBlank String digestKey, @NotNull Map<String, Object> item) {}
}
