package pl.jakubtworek.chatsystem.realtime;

import pl.jakubtworek.chatsystem.message.MessageResponse;

public record MessageSentAck(
        MessageResponse message,
        boolean duplicate
) {}
