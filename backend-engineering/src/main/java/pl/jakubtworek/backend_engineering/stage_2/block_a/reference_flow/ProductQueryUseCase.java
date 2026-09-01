package pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow;

public interface ProductQueryUseCase {
    ProductSnapshot find(String productId);
}
