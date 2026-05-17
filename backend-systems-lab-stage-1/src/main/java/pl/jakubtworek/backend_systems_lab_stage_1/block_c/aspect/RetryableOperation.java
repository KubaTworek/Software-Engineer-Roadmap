package pl.jakubtworek.backend_systems_lab_stage_1.block_c.aspect;

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