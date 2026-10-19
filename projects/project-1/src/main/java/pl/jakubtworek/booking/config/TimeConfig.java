package pl.jakubtworek.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Jedno źródło czasu ściennego dla logiki biznesowej.
 *
 * <p>Wstrzyknięty {@link Clock} pozwala testować wygaśnięcia i granice czasu bez
 * {@code Thread.sleep}. Pomiar czasu trwania operacji nadal powinien korzystać z
 * zegara monotonicznego, np. {@link System#nanoTime()}, a nie z tego beana.</p>
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock bookingClock() {
        return Clock.systemUTC();
    }
}
