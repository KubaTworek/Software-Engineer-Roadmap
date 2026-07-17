package com.example.ecommerce.common;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        List<FieldErrorItem> fields
) {
    public record FieldErrorItem(String field, String message) {}
}
