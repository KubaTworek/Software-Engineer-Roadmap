package com.example.urlshortener.enterprise;

import com.example.urlshortener.dto.CreateShortUrlResponse;
import java.util.List;

public record BulkCreateShortUrlResponse(
    int requested,
    int created,
    List<CreateShortUrlResponse> urls
) {}
