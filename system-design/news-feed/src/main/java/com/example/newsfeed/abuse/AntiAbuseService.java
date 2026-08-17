package com.example.newsfeed.abuse;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.util.UUID;

@Service
public class AntiAbuseService {
    private final AbuseSignalRepository repository;
    public AntiAbuseService(AbuseSignalRepository repository) { this.repository = repository; }

    @Transactional
    public void record(UUID userId, String type, double score, String metadata) {
        repository.save(new AbuseSignal(UUID.randomUUID(), userId, type, score, metadata, Instant.now()));
    }

    @Transactional(readOnly = true)
    public boolean isHighRisk(UUID userId) {
        return repository.totalScore(userId) >= 10.0;
    }
}
