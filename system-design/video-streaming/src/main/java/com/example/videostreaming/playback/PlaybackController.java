package com.example.videostreaming.playback;

import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.catalog.VideoStatus;
import com.example.videostreaming.storage.ObjectStorageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static com.example.videostreaming.playback.PlaybackDtos.*;

@RestController
@RequestMapping("/api/playback")
public class PlaybackController {
    private final VideoRepository videos;
    private final ObjectStorageService storage;
    private final Counter playbackStarted;

    public PlaybackController(VideoRepository videos, ObjectStorageService storage, MeterRegistry registry) {
        this.videos = videos;
        this.storage = storage;
        this.playbackStarted = Counter.builder("video_playback_started_total").register(registry);
    }

    @GetMapping("/videos/{videoId}")
    public PlaybackResponse playback(@PathVariable UUID videoId) throws Exception {
        Video video = videos.findById(videoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        if (video.getStatus() != VideoStatus.PUBLISHED || video.getHlsMasterObjectKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video is not ready for playback");
        }
        playbackStarted.increment();
        String signedUrl = storage.presignedGetUrl(video.getHlsMasterObjectKey(), 60);
        return new PlaybackResponse(video.getId(), "HLS", signedUrl, storage.cdnUrl(video.getHlsMasterObjectKey()), Instant.now().plusSeconds(3600));
    }
}
