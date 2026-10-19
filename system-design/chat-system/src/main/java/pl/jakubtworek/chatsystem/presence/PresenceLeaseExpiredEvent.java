package pl.jakubtworek.chatsystem.presence;

import java.util.UUID;

/** Publikowany po wygaśnięciu ostatniej sesji, aby realtime rozgłosił stan dopiero po commitcie cleanera. */
public record PresenceLeaseExpiredEvent(UUID userId, PresenceResponse presence) {}
