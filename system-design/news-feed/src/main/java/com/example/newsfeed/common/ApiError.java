package com.example.newsfeed.common;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        List<String> details
) {
    public static ApiError of(int status, String error, List<String> details) {
        return new ApiError(Instant.now(), status, error, details);
    }

    public static ApiError of(int status, String error, String detail) {
        return of(status, error, List.of(detail));
    }
}
