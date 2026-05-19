package pl.jakubtworek.backend_systems_lab_stage_1.block_b.polymorphism_vs_jit;

public final class MultiplyOperation implements Operation {

    @Override
    public int apply(int value) {
        return value * 2;
    }
}