package com.example.filestorage.production.cdn;

import java.time.Instant;

public record DownloadUrlResponse(String url, String delivery, Instant expiresAt) {}
