package pl.jakubtworek.chatsystem.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(@NotBlank @Size(max = 4000) String body) {}
