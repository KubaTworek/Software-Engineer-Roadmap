package com.example.notification.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateNotificationRequest(
        @NotBlank(message = "recipient is required")
        @Email(message = "recipient must be a valid email address")
        String recipient,

        @NotBlank(message = "subject is required")
        @Size(max = 150, message = "subject must not exceed 150 characters")
        String subject,

        @NotBlank(message = "message is required")
        @Size(max = 5000, message = "message must not exceed 5000 characters")
        String message
) {
}
