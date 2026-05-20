package pl.jakubtworek.backend_engineering.stage_1.block_c.aspect;

import java.lang.annotation.*;

/**
 * Custom annotation used by RetryAspect.
 *
 * Methods annotated with @RetryableOperation
 * will be intercepted by RetryAspect.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RetryableOperation {
}