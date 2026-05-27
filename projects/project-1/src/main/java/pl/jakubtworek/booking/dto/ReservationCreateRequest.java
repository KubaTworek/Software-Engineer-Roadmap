package pl.jakubtworek.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ReservationCreateRequest(
        @NotBlank String customerFullName,
        @NotBlank @Email String customerEmail
) {
}
