package pl.jakubtworek.backend_engineering.stage_1.block_b.benchmarking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents the primary dimension of a JMH experiment.
 *
 * <p>{@link BenchmarkDimension#ALLOCATION} still needs a JMH timing mode because JMH
 * always reports a primary score. Allocation conclusions must come from a profiler,
 * for example {@code -prof gc}, and its {@code gc.alloc.rate.norm} secondary metric.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Measures {

    BenchmarkDimension value();
}
