package com.example.newsfeed.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreatePostRequest(
        @NotBlank
        @Size(max = 5000)
        String content,

        @Size(max = 10)
        List<@Size(max = 50) String> topics
) {
}
