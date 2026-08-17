package pl.jakubtworek.chatsystem.realtime;

import java.security.Principal;
import java.util.UUID;

public record ChatPrincipal(UUID userId, String username) implements Principal {
    @Override
    public String getName() {
        return userId.toString();
    }
}
