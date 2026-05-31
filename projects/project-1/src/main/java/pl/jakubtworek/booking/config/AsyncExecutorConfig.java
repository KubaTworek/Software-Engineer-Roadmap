package pl.jakubtworek.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

/**
 * Konfiguracja executorów używanych w asynchronicznym flow rezerwacji.
 *
 * Ta klasa pokazuje świadome zarządzanie wątkami:
 *
 * - osobna pula dla operacji async booking flow,
 * - osobny scheduler do timeoutów,
 * - kontrolowany rozmiar puli,
 * - ograniczona kolejka,
 * - jawna strategia odrzucania zadań,
 * - poprawny shutdown przy zamykaniu aplikacji.
 *
 * To jest lepsze niż przypadkowe używanie CompletableFuture bez executora,
 * bo wtedy zadania mogą trafić do ForkJoinPool.commonPool(), który jest
 * współdzielony globalnie w JVM.
 */
@Configuration
public class AsyncExecutorConfig {

    /**
     * Główna pula wątków dla operacji asynchronicznych związanych z booking flow.
     *
     * Bean ma destroyMethod = "shutdown", więc Spring wywoła shutdown()
     * przy zamykaniu ApplicationContext.
     *
     * Dzięki temu aplikacja nie zostawi żywych wątków po zakończeniu.
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor bookingAsyncExecutor() {
        return new ThreadPoolExecutor(
                /*
                 * corePoolSize.
                 *
                 * Minimalna liczba wątków utrzymywanych w puli.
                 * Dla lekkiego async flow 4 wątki są rozsądnym punktem startowym.
                 */
                4,

                /*
                 * maximumPoolSize.
                 *
                 * Maksymalna liczba wątków, które pula może utworzyć,
                 * gdy kolejka się zapełnia.
                 */
                12,

                /*
                 * keepAliveTime.
                 *
                 * Nadmiarowe wątki powyżej corePoolSize mogą zostać usunięte
                 * po 30 sekundach bezczynności.
                 */
                30,

                /*
                 * Jednostka keepAliveTime.
                 */
                TimeUnit.SECONDS,

                /*
                 * Ograniczona kolejka zadań.
                 *
                 * To ważne: nie używamy nieograniczonej kolejki, bo przy awarii
                 * zewnętrznego systemu albo nagłym ruchu aplikacja mogłaby zacząć
                 * akumulować ogromną liczbę zadań w pamięci.
                 */
                new ArrayBlockingQueue<>(200),

                /*
                 * Własna fabryka wątków.
                 *
                 * Nazwy wątków typu booking-async-0 są dużo czytelniejsze w logach,
                 * thread dumpach, JFR i profilerze niż domyślne pool-1-thread-1.
                 */
                new NamedThreadFactory("booking-async"),

                /*
                 * Strategia odrzucania zadań.
                 *
                 * CallerRunsPolicy oznacza, że jeśli pula i kolejka są pełne,
                 * zadanie wykona wątek wywołujący.
                 *
                 * To daje naturalny backpressure: request zaczyna trwać dłużej,
                 * zamiast bez końca dokładać zadania do pamięci.
                 *
                 * Nie jest to zawsze najlepsza strategia, ale jest dobra edukacyjnie.
                 */
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * Scheduler używany do timeoutów i opóźnionych operacji.
     *
     * W projekcie async służy m.in. do zakończenia CompletableFuture timeoutem,
     * jeśli payment provider nie odpowie w wymaganym czasie.
     *
     * Używamy osobnej puli, żeby zadania timeoutowe nie konkurowały z głównymi
     * zadaniami async w bookingAsyncExecutor.
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService bookingScheduler() {
        return Executors.newScheduledThreadPool(
                2,
                new NamedThreadFactory("booking-scheduler")
        );
    }

    /**
     * Prosta ThreadFactory nadająca czytelne nazwy wątkom.
     *
     * Dzięki temu w narzędziach diagnostycznych łatwo odróżnić:
     *
     * - booking-async-* od głównego flow async,
     * - booking-scheduler-* od timeoutów/schedulera.
     */
    private static class NamedThreadFactory implements ThreadFactory {

        /**
         * Prefix nazwy wątku.
         */
        private final String prefix;

        /**
         * Domyślna fabryka wątków JDK.
         *
         * Delegujemy do niej tworzenie wątku, a potem tylko zmieniamy nazwę
         * i ustawienia.
         */
        private final ThreadFactory delegate = Executors.defaultThreadFactory();

        /**
         * Licznik używany do numerowania wątków.
         *
         * Metoda newThread jest synchronized, więc zwykły int jest tutaj bezpieczny.
         * Alternatywnie można użyć AtomicInteger.
         */
        private int counter = 0;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        /**
         * Tworzy nowy wątek dla zadania.
         *
         * synchronized chroni counter przed race condition, gdy pula tworzy kilka
         * wątków równocześnie.
         */
        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = delegate.newThread(task);

            /*
             * Czytelna nazwa wątku pomaga w:
             *
             * - logach,
             * - thread dumpach,
             * - JFR,
             * - VisualVM,
             * - IntelliJ Profilerze.
             */
            thread.setName(prefix + "-" + counter++);

            /*
             * daemon = false oznacza, że wątki są normalnymi wątkami aplikacji.
             *
             * Ponieważ Spring wywołuje shutdown() przy zamykaniu kontekstu,
             * nie musimy robić ich daemonami.
             */
            thread.setDaemon(false);

            return thread;
        }
    }
}