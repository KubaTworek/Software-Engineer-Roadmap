package pl.jakubtworek.backend.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import java.time.Clock;
import pl.jakubtworek.backend.common.web.CommonWebConfig;
import pl.jakubtworek.backend.common.web.GlobalExceptionHandler;
import pl.jakubtworek.backend.common.web.CorrelationWebClientConfig;
import pl.jakubtworek.backend.common.web.OutboundHttpClientConfig;

@SpringBootApplication
@Import({CommonWebConfig.class, GlobalExceptionHandler.class, CorrelationWebClientConfig.class, OutboundHttpClientConfig.class})
public class NotificationServiceApplication {
    @Bean Clock notificationClock() { return Clock.systemUTC(); }
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
