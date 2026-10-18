package com.example.videostreaming.live;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Koordynuje ownership procesu live pomiędzy replikami workera. */
@Service
public class LiveSessionCoordinator {
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final LiveStreamRepository streams;
    private final Clock clock;

    public LiveSessionCoordinator(LiveStreamRepository streams, Clock clock) {
        this.streams = streams;
        this.clock = clock;
    }

    @Transactional
    public Optional<Long> claim(UUID id) {
        LiveStream stream = streams.findById(id).orElseThrow();
        try {
            return Optional.of(stream.claim(clock.instant(), LEASE));
        } catch (IllegalStateException activeOwner) {
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID id, long token) {
        return streams.findById(id).map(stream -> stream.ownedBy(token, clock.instant())).orElse(false);
    }

    @Transactional
    public boolean renew(UUID id, long token) {
        return streams.findById(id).map(stream -> {
            try {
                stream.renew(token, clock.instant(), LEASE);
                return true;
            } catch (IllegalStateException staleOwner) {
                return false;
            }
        }).orElse(false);
    }

    @Transactional
    public void live(UUID id, long token, String masterKey, String recordingKey) {
        streams.findById(id).orElseThrow().markLive(token, clock.instant(), masterKey, recordingKey);
    }

    @Transactional
    public void ended(UUID id, long token) {
        LiveStream stream = streams.findById(id).orElseThrow();
        if (stream.getStatus() != LiveStatus.VOD_READY) stream.markEnded(token, clock.instant());
    }

    @Transactional
    public void failed(UUID id, long token, String error) {
        streams.findById(id).orElseThrow().markFailed(token, clock.instant(), error);
    }

    @Transactional
    public void stopAndFence(UUID id) {
        streams.findById(id).ifPresent(LiveStream::stopAndFence);
    }
}
