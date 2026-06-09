package com.example.newsfeed.media;
import jakarta.validation.constraints.NotBlank;
public record CreateMediaUploadRequest(@NotBlank String filename, @NotBlank String mediaType) {}
