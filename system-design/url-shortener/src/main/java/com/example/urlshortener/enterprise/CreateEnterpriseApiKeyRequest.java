package com.example.urlshortener.enterprise;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateEnterpriseApiKeyRequest(
    @NotBlank @Size(max = 128) String name,
    @Size(max = 64) String tier,
    @Min(1) Integer rateLimitPerMinute,
    @Future Instant expiresAt
) {}
