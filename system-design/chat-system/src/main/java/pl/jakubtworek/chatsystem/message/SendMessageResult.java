package pl.jakubtworek.chatsystem.message;

public record SendMessageResult(
        MessageResponse message,
        boolean duplicate
) {}
