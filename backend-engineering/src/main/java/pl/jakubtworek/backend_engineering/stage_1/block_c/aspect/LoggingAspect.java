package pl.jakubtworek.backend_engineering.stage_1.block_c.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Simple logging aspect.
 *
 * @Before advice executes BEFORE target method.
 */
@Aspect
@Component
@Order(1)
public class LoggingAspect {

    /**
     * Pointcut expression:
     *
     * execution(* demo.aop.service.*.*(..))
     *
     * Means:
     * - any return type,
     * - any method,
     * - any arguments,
     * - inside demo.aop.service package.
     */
    @Before("execution(* demo.aop.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {

        System.out.println(
                "[LOG BEFORE] Method called: "
                        + joinPoint.getSignature()
        );
    }

    /**
     * Executes after successful method execution.
     */
    @AfterReturning(
            pointcut = "execution(* demo.aop.service.*.*(..))",
            returning = "result"
    )
    public void logAfterReturning(
            JoinPoint joinPoint,
            Object result
    ) {
        System.out.println(
                "[LOG AFTER RETURNING] Method: "
                        + joinPoint.getSignature()
                        + " returned: "
                        + result
        );
    }

    /**
     * Executes when exception is thrown.
     */
    @AfterThrowing(
            pointcut = "execution(* demo.aop.service.*.*(..))",
            throwing = "exception"
    )
    public void logAfterThrowing(
            JoinPoint joinPoint,
            Throwable exception
    ) {
        System.out.println(
                "[LOG AFTER THROWING] Method: "
                        + joinPoint.getSignature()
                        + " failed with: "
                        + exception.getMessage()
        );
    }

    /**
     * Executes after method completion
     * regardless of success or failure.
     */
    @After("execution(* demo.aop.service.*.*(..))")
    public void logAfter(JoinPoint joinPoint) {

        System.out.println(
                "[LOG AFTER] Method finished: "
                        + joinPoint.getSignature()
        );
    }
}