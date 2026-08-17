package com.example.newsfeed.media;
import com.example.newsfeed.auth.CurrentUser; import com.example.newsfeed.user.User;
import jakarta.validation.Valid; import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaService mediaService;
    public MediaController(MediaService mediaService) { this.mediaService = mediaService; }

    @PostMapping("/uploads")
    public MediaUploadResponse createUpload(@CurrentUser User user, @Valid @RequestBody CreateMediaUploadRequest request) {
        return mediaService.createUpload(user, request);
    }
}
