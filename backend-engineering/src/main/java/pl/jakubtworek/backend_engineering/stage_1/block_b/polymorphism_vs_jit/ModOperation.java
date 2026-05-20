package pl.jakubtworek.backend_engineering.stage_1.block_b.polymorphism_vs_jit;

public final class ModOperation implements Operation {

    @Override
    public int apply(int value) {
        return value % 10;
    }
}