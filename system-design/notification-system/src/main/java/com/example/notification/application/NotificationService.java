package com.example.notification.application;

import com.example.notification.domain.Notification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;

    public NotificationService(NotificationRepository notificationRepository, EmailSender emailSender) {
        this.notificationRepository = notificationRepository;
        this.emailSender = emailSender;
    }

    public Notification sendEmailNotification(String recipient, String subject, String message) {
        Notification notification = Notification.createEmail(recipient, subject, message);
        notificationRepository.save(notification);

        try {
            emailSender.send(notification);
            notification.markAsSent();
        } catch (RuntimeException exception) {
            notification.markAsFailed(exception.getMessage());
        }

        return notificationRepository.save(notification);
    }

    public Notification getNotification(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    public List<Notification> getAllNotifications() {
        return notificationRepository.findAll();
    }
}
