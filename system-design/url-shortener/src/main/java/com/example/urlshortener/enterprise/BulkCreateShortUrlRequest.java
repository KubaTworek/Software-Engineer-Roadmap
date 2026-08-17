package com.example.urlshortener.enterprise;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkCreateShortUrlRequest(
    @NotEmpty List<@Valid CreateShortUrlRequest> urls
) {}
