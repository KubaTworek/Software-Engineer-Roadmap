package pl.jakubtworek.backend_systems_lab_stage_1.block_c.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

/**
 * Demonstrates retry mechanism implemented using AOP.
 *
 * Similar concepts are used internally
 * by frameworks like Spring Retry.
 */
@Aspect
@Component
public class RetryAspect {

    private static final int MAX_RETRIES = 3;

    /**
     * Retries failed method execution.
     */
    @Around("@annotation(demo.aop.retry.RetryableOperation)")
    public Object retry(
            ProceedingJoinPoint proceedingJoinPoint
    ) throws Throwable {

        int attempts = 0;

        while (true) {

            try {

                attempts++;

                System.out.println(
                        "[RETRY] Attempt: " + attempts
                );

                return proceedingJoinPoint.proceed();

            } catch (Exception exception) {

                if (attempts >= MAX_RETRIES) {

                    System.out.println(
                            "[RETRY] Max retries exceeded"
                    );

                    throw exception;
                }

                System.out.println(
                        "[RETRY] Retrying after failure"
                );
            }
        }
    }
}