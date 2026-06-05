package com.example.videostreaming.catalog;

import com.example.videostreaming.auth.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.example.videostreaming.catalog.CatalogDtos.*;

@RestController
@RequestMapping("/api/videos")
public class CatalogController {
    private final VideoRepository videos;

    public CatalogController(VideoRepository videos) { this.videos = videos; }

    @GetMapping
    public Page<VideoResponse> published(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return videos.findByStatusAndVisibilityOrderByPublishedAtDesc(
                VideoStatus.PUBLISHED, VideoVisibility.PUBLIC, PageRequest.of(page, Math.min(size, 100))
        ).map(VideoResponse::from);
    }

    @GetMapping("/{id}")
    public VideoResponse get(@PathVariable UUID id) {
        Video video = videos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        if (video.getStatus() != VideoStatus.PUBLISHED && video.getVisibility() != VideoVisibility.PUBLIC) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
        }
        return VideoResponse.from(video);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public VideoResponse create(@Valid @RequestBody CreateVideoRequest request, @AuthenticationPrincipal User user) {
        return VideoResponse.from(videos.save(new Video(request.title(), request.description(), user)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public VideoResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVideoRequest request) {
        Video video = videos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        video.updateMetadata(request.title(), request.description());
        return VideoResponse.from(videos.save(video));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public VideoResponse publish(@PathVariable UUID id) {
        Video video = videos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        if (video.getStatus() != VideoStatus.READY && video.getStatus() != VideoStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Video must be READY before publishing");
        }
        video.publish();
        return VideoResponse.from(videos.save(video));
    }
}
