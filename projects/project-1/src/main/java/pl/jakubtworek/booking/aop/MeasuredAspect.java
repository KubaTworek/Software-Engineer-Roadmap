package pl.jakubtworek.booking.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MeasuredAspect {
    private final MeasurementRegistry measurementRegistry;

    public MeasuredAspect(MeasurementRegistry measurementRegistry) {
        this.measurementRegistry = measurementRegistry;
    }

    @Around("@annotation(pl.jakubtworek.booking.aop.Measured)")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.nanoTime() - start;
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Measured measured = signature.getMethod().getAnnotation(Measured.class);
            measurementRegistry.record(new MeasuredCall(
                    signature.getDeclaringType().getSimpleName() + "." + signature.getName(),
                    measured.value(),
                    duration
            ));
        }
    }
}
