package pl.jakubtworek.backend_engineering.stage_1.block_e.refactored;

import java.time.LocalDate;

public record RegisterUserOutput(Long userId, String username, String email, LocalDate registeredAt) { }
