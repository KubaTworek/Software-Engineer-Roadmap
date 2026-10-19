package com.example.notification.api;

import com.example.notification.api.dto.ApiDtos;
import com.example.notification.application.NotificationService;
import com.example.notification.application.Ports;
import com.example.notification.domain.ContactPoint;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

/** Publiczna granica intencji; nie wykonuje efektu providera w wątku HTTP. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;
    private final Ports.NotificationRepository notifications;
    private final Ports.NotificationJobRepository jobs;
    public NotificationController(NotificationService service, Ports.NotificationRepository notifications,
                                  Ports.NotificationJobRepository jobs) {
        this.service = service;
        this.notifications = notifications;
        this.jobs = jobs;
    }

    /**
     * `202` potwierdza trwałe przyjęcie intencji, nie dostarczenie wiadomości.
     * Idempotencja, consent i zapis outboxa należą do use case'u, nie kontrolera.
     */
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiDtos.CreateNotificationResponse create(@RequestHeader(value="X-Actor",required=false) String actor,
                                                      @Valid @RequestBody ApiDtos.CreateNotificationRequest r) {
        var cp=r.contactPoint();
        return ApiDtos.CreateNotificationResponse.from(service.create(TenantContext.getTenantId(),actor,r.userId(),
                r.notificationType(),r.channels(),new ContactPoint(cp.email(),cp.phoneNumber(),cp.pushToken()),
                r.payload(),r.idempotencyKey(),r.expiresAt(),r.scheduledAt()));
    }
    @GetMapping public List<ApiDtos.NotificationResponse> all() {
        return notifications.findAll(TenantContext.getTenantId()).stream()
                .map(ApiDtos.NotificationResponse::from).toList();
    }
    @GetMapping("/{id}") public ApiDtos.NotificationResponse one(@PathVariable UUID id) {
        return notifications.findById(TenantContext.getTenantId(), id)
                .map(ApiDtos.NotificationResponse::from).orElseThrow(this::notFound);
    }
    /** Najpierw autoryzujemy zasób nadrzędny, aby identyfikator nie omijał granicy tenanta. */
    @GetMapping("/{id}/jobs") public List<ApiDtos.NotificationJobResponse> jobs(@PathVariable UUID id) {
        notifications.findById(TenantContext.getTenantId(), id).orElseThrow(this::notFound);
        return jobs.findByNotificationId(TenantContext.getTenantId(), id).stream()
                .map(ApiDtos.NotificationJobResponse::from).toList();
    }
    @DeleteMapping("/{id}") public ApiDtos.NotificationResponse cancel(
            @RequestHeader(value="X-Actor",required=false) String actor, @PathVariable UUID id) {
        return ApiDtos.NotificationResponse.from(service.cancel(TenantContext.getTenantId(), actor, id));
    }
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
    }
}
