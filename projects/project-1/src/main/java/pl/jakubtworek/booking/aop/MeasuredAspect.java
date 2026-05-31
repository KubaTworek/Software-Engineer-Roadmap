package pl.jakubtworek.booking.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Aspekt mierzący czas wykonania metod oznaczonych adnotacją @Measured.
 *
 * To jest część etapu "Spring pod maską".
 *
 * Klasa pokazuje:
 *
 * - jak działa Spring AOP,
 * - jak używać @Around,
 * - jak przechwycić wywołanie metody,
 * - jak wykonać kod przed i po metodzie,
 * - jak odczytać adnotację z metody,
 * - jak zapisać pomiar do rejestru.
 *
 * Ważne:
 * Spring AOP działa przez proxy. To oznacza, że aspekt wykona się tylko wtedy,
 * gdy wywołanie metody przejdzie przez proxy Springa.
 *
 * Wywołanie metody z tej samej klasy przez this zwykle ominie aspekt.
 */
@Aspect
@Component
public class MeasuredAspect {

    /**
     * Rejestr pomiarów.
     *
     * Aspekt nie zwraca pomiaru bezpośrednio do klienta.
     * Zapisuje go w MeasurementRegistry, żeby później endpoint edukacyjny mógł
     * pokazać, czy aspekt faktycznie się wykonał.
     */
    private final MeasurementRegistry measurementRegistry;

    /**
     * Constructor injection.
     *
     * MeasurementRegistry jest zwykłym beanem Springa wstrzykniętym do aspektu.
     */
    public MeasuredAspect(MeasurementRegistry measurementRegistry) {
        this.measurementRegistry = measurementRegistry;
    }

    /**
     * Advice typu @Around.
     *
     * @Around pozwala wykonać kod:
     *
     * - przed metodą,
     * - zamiast metody,
     * - po metodzie,
     * - także wtedy, gdy metoda rzuci wyjątek.
     *
     * Pointcut:
     *
     * @annotation(pl.jakubtworek.booking.aop.Measured)
     *
     * oznacza:
     * przechwyć metody oznaczone adnotacją @Measured.
     */
    @Around("@annotation(pl.jakubtworek.booking.aop.Measured)")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        /*
         * Pomiar zaczynamy przed wykonaniem metody.
         *
         * System.nanoTime() jest lepszy do mierzenia czasu trwania niż
         * currentTimeMillis(), bo jest monotoniczny względem pomiarów czasu.
         */
        long start = System.nanoTime();

        try {
            /*
             * joinPoint.proceed() uruchamia oryginalną metodę.
             *
             * Bez tego metoda przechwycona przez aspekt w ogóle by się nie wykonała.
             *
             * Zwracamy wynik proceed(), żeby zachować oryginalne zachowanie metody.
             */
            return joinPoint.proceed();
        } finally {
            /*
             * finally wykona się zarówno po sukcesie, jak i po wyjątku.
             *
             * Dzięki temu mierzymy także metody, które zakończyły się błędem.
             */
            long duration = System.nanoTime() - start;

            /*
             * Signature opisuje przechwyconą metodę.
             *
             * Rzutujemy na MethodSignature, bo chcemy dostać dostęp do refleksyjnego
             * obiektu Method i odczytać z niego adnotację @Measured.
             */
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();

            /*
             * Odczytujemy adnotację @Measured z metody.
             *
             * measured.value() może zawierać nazwę pomiaru, np.
             * "spring-pitfall-measured-operation".
             */
            Measured measured = signature.getMethod().getAnnotation(Measured.class);

            /*
             * Zapisujemy pomiar do rejestru.
             *
             * measuredCall zawiera:
             * - nazwę metody,
             * - nazwę logiczną z adnotacji @Measured,
             * - czas wykonania w nanosekundach.
             */
            measurementRegistry.record(new MeasuredCall(
                    signature.getDeclaringType().getSimpleName() + "." + signature.getName(),
                    measured.value(),
                    duration
            ));
        }
    }
}