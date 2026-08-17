package pl.jakubtworek.chatsystem.message;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReceiptRequest(
        @NotNull UUID messageId
) {}
