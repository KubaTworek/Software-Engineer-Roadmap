package com.example.filestorage.production.processing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Atomowo dzieli pracę między instancje i odzyskuje wygasłe lease'y. */
@Service
public class ProcessingJobClaims {
    private final FileProcessingJobRepository jobs;
    private final Clock clock;

    public ProcessingJobClaims(FileProcessingJobRepository jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional
    public List<UUID> claim(int batchSize) {
        var now = clock.instant();
        var claimed = jobs.claimableBatch(now, now.minus(Duration.ofMinutes(5)), batchSize);
        claimed.forEach(job -> job.claim(now));
        return claimed.stream().map(FileProcessingJob::getId).toList();
    }
}
