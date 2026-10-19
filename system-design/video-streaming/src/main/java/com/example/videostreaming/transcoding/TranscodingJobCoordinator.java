package com.example.videostreaming.transcoding;

import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Krótkie transakcje DB otaczające długą, nietransakcyjną pracę FFmpeg/storage. */
@Service
public class TranscodingJobCoordinator {
    private static final Duration LEASE = Duration.ofHours(2);
    private final TranscodingJobRepository jobs;
    private final VideoRepository videos;
    private final Clock clock;

    public TranscodingJobCoordinator(TranscodingJobRepository jobs, VideoRepository videos, Clock clock) {
        this.jobs = jobs;
        this.videos = videos;
        this.clock = clock;
    }

    @Transactional
    public Optional<ClaimedJob> claim(UUID jobId) {
        TranscodingJob job = jobs.findById(jobId).orElseThrow();
        if (job.getStatus() == TranscodingJobStatus.COMPLETED
                || job.getStatus() == TranscodingJobStatus.DEAD_LETTER) {
            return Optional.empty();
        }
        try {
            long token = job.claim(clock.instant(), LEASE);
            jobs.saveAndFlush(job);
            return Optional.of(new ClaimedJob(job.getId(), job.getVideo().getId(), job.getSourceObjectKey(),
                    job.getAttempts(), token));
        } catch (IllegalStateException activeLease) {
            return Optional.empty();
        }
    }

    @Transactional
    public void complete(ClaimedJob claim, String masterKey) {
        TranscodingJob job = jobs.findById(claim.jobId()).orElseThrow();
        Video video = videos.findById(claim.videoId()).orElseThrow();
        job.completed(claim.fencingToken(), clock.instant());
        video.markReady(masterKey);
    }

    @Transactional
    public void retry(ClaimedJob claim, String error) {
        jobs.findById(claim.jobId()).orElseThrow()
                .retrying(claim.fencingToken(), error, clock.instant());
    }

    @Transactional
    public void deadLetter(ClaimedJob claim, String error) {
        TranscodingJob job = jobs.findById(claim.jobId()).orElseThrow();
        job.deadLetter(claim.fencingToken(), error, clock.instant());
        videos.findById(claim.videoId()).ifPresent(Video::markFailed);
    }

    public record ClaimedJob(UUID jobId, UUID videoId, String sourceObjectKey, int attempt, long fencingToken) {}
}
