package pl.jakubtworek.backend_systems_lab_stage_1.block_c.aspect;

import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Demonstrates aspect ordering.
 *
 * Lower order value = higher priority.
 *
 * Aspect execution order matters when:
 * - transactions,
 * - security,
 * - caching,
 * - logging
 *
 * interact together.
 */
@Aspect
@Component
@Order(0)
public class SecurityAspect {

    /**
     * Simulates security check before service execution.
     */
    @Before("execution(* demo.aop.service.*.*(..))")
    public void authorize() {

        System.out.println(
                "[SECURITY] Authorization check passed"
        );
    }
}