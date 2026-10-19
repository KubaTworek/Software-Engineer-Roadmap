package com.example.urlshortener.enterprise;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDestinationRequest(@NotBlank @Size(max = 4096) String longUrl) {}
