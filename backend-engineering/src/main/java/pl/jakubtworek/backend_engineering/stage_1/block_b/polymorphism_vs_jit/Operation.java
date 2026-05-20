package pl.jakubtworek.backend_engineering.stage_1.block_b.polymorphism_vs_jit;

public interface Operation {

    // This interface is intentionally simple.
    // The benchmark focuses on how the JIT treats virtual dispatch
    // depending on call-site polymorphism.
    int apply(int value);
}