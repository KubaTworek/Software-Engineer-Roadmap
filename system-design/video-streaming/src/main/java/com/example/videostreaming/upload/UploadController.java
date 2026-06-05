package com.example.videostreaming.upload;

import com.example.videostreaming.catalog.Video;
import com.example.videostreaming.catalog.VideoRepository;
import com.example.videostreaming.storage.ObjectStorageService;
import com.example.videostreaming.storage.StorageProperties;
import com.example.videostreaming.transcoding.TranscodingJob;
import com.example.videostreaming.transcoding.TranscodingJobRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static com.example.videostreaming.upload.UploadDtos.*;

@RestController
@RequestMapping("/api/videos/{videoId}/uploads")
@PreAuthorize("hasRole('ADMIN')")
public class UploadController {
    private final VideoRepository videos;
    private final UploadRepository uploads;
    private final TranscodingJobRepository jobs;
    private final ObjectStorageService storage;
    private final StorageProperties props;

    public UploadController(VideoRepository videos, UploadRepository uploads, TranscodingJobRepository jobs,
                            ObjectStorageService storage, StorageProperties props) {
        this.videos = videos;
        this.uploads = uploads;
        this.jobs = jobs;
        this.storage = storage;
        this.props = props;
    }

    @PostMapping
    public CreateUploadResponse create(@PathVariable UUID videoId, @Valid @RequestBody CreateUploadRequest request) throws Exception {
        Video video = videos.findById(videoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        String safeFilename = request.filename().replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectKey = "raw/" + videoId + "/" + UUID.randomUUID() + "-" + safeFilename;
        Upload upload = uploads.save(new Upload(video, objectKey, request.filename(), request.contentType(), request.sizeBytes()));
        video.markUploading();
        videos.save(video);
        return new CreateUploadResponse(upload.getId(), videoId, objectKey, storage.presignedPutUrl(objectKey), "PUT", props.presignedUploadExpiryMinutes());
    }

    @PostMapping("/{uploadId}/complete")
    public CompleteUploadResponse complete(@PathVariable UUID videoId, @PathVariable UUID uploadId) {
        Video video = videos.findById(videoId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        Upload upload = uploads.findById(uploadId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found"));
        if (!upload.getVideo().getId().equals(video.getId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload does not belong to video");
        upload.complete();
        uploads.save(upload);
        video.markUploaded(upload.getObjectKey());
        video.markProcessing();
        videos.save(video);
        jobs.save(new TranscodingJob(video, upload.getObjectKey()));
        return new CompleteUploadResponse(upload.getId(), video.getId(), "COMPLETED", "PENDING");
    }
}
