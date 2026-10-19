package com.example.newsfeed.media;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
public record CreateMediaUploadRequest(
        @NotBlank String filename,
        @NotBlank String mediaType,
        @Positive Long sizeBytes
) {}
