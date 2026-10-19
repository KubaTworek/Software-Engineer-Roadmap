package com.example.filestorage.production.processing;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Scheduler nie trzyma jednej transakcji podczas I/O całej paczki. */
@Component
@ConditionalOnProperty(name = "app.production.workers.enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundProcessingWorker {
    private final ProcessingJobClaims claims;
    private final ProcessingJobExecutor executor;

    public BackgroundProcessingWorker(ProcessingJobClaims claims, ProcessingJobExecutor executor) {
        this.claims = claims;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${app.production.workers.processing-fixed-delay-ms:10000}")
    public void processPendingJobs() {
        claims.claim(25).forEach(executor::execute);
    }
}
