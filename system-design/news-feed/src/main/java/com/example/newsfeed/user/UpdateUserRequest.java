package com.example.newsfeed.user;

import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 2, max = 120)
        String displayName,

        @Size(max = 500)
        String bio
) {
}
