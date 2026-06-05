package com.example.videostreaming.transcoding;

import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.storage.ObjectStorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class TranscodingWorker {
    private static final Logger log = LoggerFactory.getLogger(TranscodingWorker.class);

    private final TranscodingJobRepository jobs;
    private final VideoRepository videos;
    private final ObjectStorageService storage;
    private final TranscodingProperties props;
    private final Counter successCounter;
    private final Counter failureCounter;

    public TranscodingWorker(TranscodingJobRepository jobs, VideoRepository videos, ObjectStorageService storage,
                             TranscodingProperties props, MeterRegistry meterRegistry) {
        this.jobs = jobs;
        this.videos = videos;
        this.storage = storage;
        this.props = props;
        this.successCounter = Counter.builder("video_transcoding_success_total").register(meterRegistry);
        this.failureCounter = Counter.builder("video_transcoding_failure_total").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${app.transcoding.poll-interval-ms}")
    @Transactional
    public void poll() {
        if (!props.enabled()) return;
        List<TranscodingJob> pending = jobs.findByStatusOrderByCreatedAtAsc(TranscodingJobStatus.PENDING, PageRequest.of(0, 1));
        pending.forEach(this::process);
    }

    private void process(TranscodingJob job) {
        UUID videoId = job.getVideo().getId();
        Path jobDir = Path.of(props.workDir(), job.getId().toString());
        Path input = jobDir.resolve("source");
        Path outputDir = jobDir.resolve("hls");
        try {
            job.running();
            jobs.saveAndFlush(job);
            Video video = videos.findById(videoId).orElseThrow();
            video.markProcessing();
            videos.saveAndFlush(video);

            Files.createDirectories(outputDir);
            log.info("Downloading raw video {} for job {}", job.getSourceObjectKey(), job.getId());
            storage.download(job.getSourceObjectKey(), input);

            runFfmpeg(input, outputDir);

            String prefix = "videos/" + videoId + "/hls";
            uploadDirectory(outputDir, prefix);
            String masterKey = prefix + "/master.m3u8";
            video.markReady(masterKey);
            videos.save(video);
            job.completed();
            jobs.save(job);
            successCounter.increment();
            log.info("Transcoding completed for video {}", videoId);
        } catch (Exception ex) {
            log.error("Transcoding failed for job {}", job.getId(), ex);
            job.failed(ex.getMessage());
            jobs.save(job);
            videos.findById(videoId).ifPresent(video -> { video.markFailed(); videos.save(video); });
            failureCounter.increment();
        } finally {
            deleteQuietly(jobDir);
        }
    }

    private void runFfmpeg(Path input, Path outputDir) throws IOException, InterruptedException {
        // MVP ladder: single HLS rendition to keep local CPU usage sane.
        // Extend with var_stream_map for 360p/720p/1080p in production.
        Path master = outputDir.resolve("master.m3u8");
        List<String> command = List.of(
                props.ffmpegPath(), "-y",
                "-i", input.toString(),
                "-c:v", "h264", "-preset", "veryfast", "-crf", "23",
                "-c:a", "aac", "-b:a", "128k",
                "-hls_time", String.valueOf(props.hlsSegmentSeconds()),
                "-hls_playlist_type", "vod",
                "-hls_segment_filename", outputDir.resolve("segment_%05d.ts").toString(),
                master.toString()
        );
        Process process = new ProcessBuilder(command).redirectErrorStream(true).inheritIO().start();
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("ffmpeg exited with code " + exit);
    }

    private void uploadDirectory(Path dir, String prefix) throws Exception {
        try (var stream = Files.walk(dir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                String contentType = name.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t";
                storage.uploadFile(prefix + "/" + name, file, contentType);
            }
        }
    }

    private void deleteQuietly(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                    });
                }
            }
        } catch (Exception ignored) {}
    }
}
