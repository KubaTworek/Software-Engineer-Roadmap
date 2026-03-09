package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.race_condition;

public record Result(
        int initialStock,
        int finalStock,
        int threads,
        String storeType
) {}