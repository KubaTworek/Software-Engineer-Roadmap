package pl.jakubtworek.backend_engineering.stage_1.block_c.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Around advice gives full control over method execution.
 *
 * It can:
 * - execute logic before method,
 * - execute logic after method,
 * - modify arguments,
 * - modify return value,
 * - retry execution,
 * - block execution.
 */
@Aspect
@Component
@Order(2)
public class PerformanceAspect {

    /**
     * Measures execution time of service methods.
     *
     * IMPORTANT:
     * proceed() MUST be called,
     * otherwise target method will never execute.
     */
    @Around("execution(* demo.aop.service.*.*(..))")
    public Object measureExecutionTime(
            ProceedingJoinPoint proceedingJoinPoint
    ) throws Throwable {

        long start = System.currentTimeMillis();

        try {

            /**
             * Executes original target method.
             */
            Object result = proceedingJoinPoint.proceed();

            long executionTime =
                    System.currentTimeMillis() - start;

            System.out.println(
                    "[PERFORMANCE] "
                            + proceedingJoinPoint.getSignature()
                            + " executed in "
                            + executionTime
                            + " ms"
            );

            return result;

        } catch (Throwable throwable) {

            System.out.println(
                    "[PERFORMANCE] Exception detected"
            );

            throw throwable;
        }
    }
}