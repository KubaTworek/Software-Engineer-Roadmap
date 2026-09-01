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
     * The pointcut selects Spring {@code @Service} beans. Using an annotation
     * boundary keeps the example valid after moving classes between packages.
     */
    @Before("@within(org.springframework.stereotype.Service)")
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
            pointcut = "@within(org.springframework.stereotype.Service)",
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
            pointcut = "@within(org.springframework.stereotype.Service)",
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
    @After("@within(org.springframework.stereotype.Service)")
    public void logAfter(JoinPoint joinPoint) {

        System.out.println(
                "[LOG AFTER] Method finished: "
                        + joinPoint.getSignature()
        );
    }
}
