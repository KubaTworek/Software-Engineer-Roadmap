package pl.jakubtworek.backend_engineering.stage_1.block_a.race_condition;

public record Result(
        int initialStock,
        int finalStock,
        int threads,
        String storeType
) {}