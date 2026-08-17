package com.example.newsfeed.media;
import java.util.UUID;
public record MediaUploadResponse(UUID mediaId, String uploadUrl, String publicUrl, String status) {}
