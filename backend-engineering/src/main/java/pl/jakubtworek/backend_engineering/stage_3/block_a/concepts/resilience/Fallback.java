package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.resilience;

/**
 * Produces a controlled result after the protected operation has finally failed.
 *
 * <p>A fallback must preserve business meaning. Returning an empty recommendation list
 * can be safe; reporting a payment as accepted without confirmation cannot.</p>
 */
@FunctionalInterface
public interface Fallback<T> {

    T recover(Exception failure) throws Exception;
}
