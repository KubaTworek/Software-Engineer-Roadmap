package pl.jakubtworek.chatsystem.moderation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportMessageRequest(
        @NotBlank @Size(max = 120) String reason,
        @Size(max = 2000) String details
) {}
