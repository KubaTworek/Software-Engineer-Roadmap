package com.example.autocomplete.api;

import java.time.Instant;

public record ErrorResponse(String message, Instant timestamp) {
}
