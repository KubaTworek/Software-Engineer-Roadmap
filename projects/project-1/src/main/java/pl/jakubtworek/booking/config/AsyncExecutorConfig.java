package pl.jakubtworek.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class AsyncExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor bookingAsyncExecutor() {
        return new ThreadPoolExecutor(
                4,
                12,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                new NamedThreadFactory("booking-async"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService bookingScheduler() {
        return Executors.newScheduledThreadPool(2, new NamedThreadFactory("booking-scheduler"));
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final ThreadFactory delegate = Executors.defaultThreadFactory();
        private int counter = 0;

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public synchronized Thread newThread(Runnable task) {
            Thread thread = delegate.newThread(task);
            thread.setName(prefix + "-" + counter++);
            thread.setDaemon(false);
            return thread;
        }
    }
}
