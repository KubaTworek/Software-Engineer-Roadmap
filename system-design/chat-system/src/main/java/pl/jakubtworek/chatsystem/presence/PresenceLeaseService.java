package pl.jakubtworek.chatsystem.presence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Presence jest pochodną aktywnych lease'ów; TTL naprawia utracony disconnect i restart gatewaya. */
@Service
public class PresenceLeaseService {
    private final PresenceSessionRepository sessions;
    private final PresenceService presence;
    private final Duration ttl;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final String instanceId = ManagementFactory.getRuntimeMXBean().getName();

    public PresenceLeaseService(PresenceSessionRepository sessions, PresenceService presence,
            @Value("${app.websocket.presence-lease-seconds:45}") long ttlSeconds, Clock clock,
            ApplicationEventPublisher events) {
        this.sessions = sessions; this.presence = presence; this.ttl = Duration.ofSeconds(ttlSeconds); this.clock = clock;
        this.events = events;
    }

    @Transactional
    public Optional<PresenceResponse> connected(String sessionId, UUID userId) {
        Instant now = clock.instant();
        sessions.save(new PresenceSession(sessionId, userId, instanceId, now.plus(ttl)));
        return sessions.countByUserIdAndExpiresAtAfter(userId, now) == 1
                ? Optional.of(presence.markOnline(userId)) : Optional.empty();
    }

    @Transactional
    public void heartbeat(String sessionId, UUID userId) {
        sessions.save(new PresenceSession(sessionId, userId, instanceId, clock.instant().plus(ttl)));
    }

    @Transactional
    public Optional<PresenceResponse> disconnected(String sessionId, UUID userId) {
        sessions.deleteById(sessionId);
        return sessions.countByUserIdAndExpiresAtAfter(userId, clock.instant()) == 0
                ? Optional.of(presence.markOffline(userId)) : Optional.empty();
    }

    @Scheduled(fixedDelayString = "${app.websocket.presence-cleanup-ms:15000}")
    @Transactional
    public void expireLeases() {
        Instant now = clock.instant();
        var affected = sessions.findUsersWithExpiredSessions(now);
        sessions.deleteExpired(now);
        affected.stream().filter(user -> sessions.countByUserIdAndExpiresAtAfter(user, now) == 0)
                .map(user -> new PresenceLeaseExpiredEvent(user, presence.markOffline(user)))
                .forEach(events::publishEvent);
    }
}
