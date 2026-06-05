package com.example.videostreaming.watch;

import com.example.videostreaming.auth.User;
import com.example.videostreaming.catalog.VideoRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.example.videostreaming.watch.WatchDtos.*;

@RestController
@RequestMapping("/api/watch-progress")
public class WatchProgressController {
    private final WatchProgressRepository progress;
    private final VideoRepository videos;

    public WatchProgressController(WatchProgressRepository progress, VideoRepository videos) {
        this.progress = progress;
        this.videos = videos;
    }

    @PutMapping("/{videoId}")
    public ProgressResponse save(@PathVariable UUID videoId, @Valid @RequestBody SaveProgressRequest request,
                                 @AuthenticationPrincipal User user) {
        if (!videos.existsById(videoId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        WatchProgressId id = new WatchProgressId(user.getId(), videoId);
        WatchProgress entity = progress.findById(id)
                .orElseGet(() -> new WatchProgress(user.getId(), videoId, request.positionSeconds(), request.durationSeconds()));
        entity.update(request.positionSeconds(), request.durationSeconds());
        return ProgressResponse.from(progress.save(entity));
    }

    @GetMapping("/{videoId}")
    public ProgressResponse get(@PathVariable UUID videoId, @AuthenticationPrincipal User user) {
        WatchProgressId id = new WatchProgressId(user.getId(), videoId);
        return progress.findById(id).map(ProgressResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Progress not found"));
    }
}
