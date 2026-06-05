package com.example.notification.api;

import com.example.notification.application.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse createNotification(@Valid @RequestBody CreateNotificationRequest request) {
        return NotificationResponse.from(
                notificationService.sendEmailNotification(
                        request.recipient(),
                        request.subject(),
                        request.message()
                )
        );
    }

    @GetMapping("/{id}")
    public NotificationResponse getNotification(@PathVariable UUID id) {
        return NotificationResponse.from(notificationService.getNotification(id));
    }

    @GetMapping
    public List<NotificationResponse> getNotifications() {
        return notificationService.getAllNotifications()
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
