package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

/**
 * Aspect = additional behavior around methods.
 *
 * Implemented using Spring Proxy mechanism.
 */
@Aspect
@Component
public class LoggingBeanAspect {

    /**
     * Intercepts all methods from PaymentServiceImpl.
     */
    @Around("execution(* demo.proxy.PaymentServiceImpl.*(..))")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {

        System.out.println(">>> BEFORE METHOD");

        Object result = joinPoint.proceed();

        System.out.println("<<< AFTER METHOD");

        return result;
    }
}