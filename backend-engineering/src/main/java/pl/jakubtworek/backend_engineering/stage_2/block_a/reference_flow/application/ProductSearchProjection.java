package pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.application;

public interface ProductSearchProjection {
    boolean apply(ProductChangedMessage message);
}
