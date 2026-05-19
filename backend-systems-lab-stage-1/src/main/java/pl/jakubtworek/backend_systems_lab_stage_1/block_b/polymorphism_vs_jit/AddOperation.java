package pl.jakubtworek.backend_systems_lab_stage_1.block_b.polymorphism_vs_jit;

public final class AddOperation implements Operation {

    @Override
    public int apply(int value) {
        // Very small methods are usually ideal candidates for inlining.
        return value + 1;
    }
}