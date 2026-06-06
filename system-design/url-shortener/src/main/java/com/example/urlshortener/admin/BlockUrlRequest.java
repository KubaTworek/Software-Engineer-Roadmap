package com.example.urlshortener.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlockUrlRequest(
    @NotBlank
    @Size(max = 500)
    String reason
) {}
