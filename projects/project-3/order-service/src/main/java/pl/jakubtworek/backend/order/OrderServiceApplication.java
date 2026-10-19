package pl.jakubtworek.backend.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import pl.jakubtworek.backend.common.web.CommonWebConfig;
import pl.jakubtworek.backend.common.web.GlobalExceptionHandler;
import pl.jakubtworek.backend.common.web.CorrelationWebClientConfig;
import pl.jakubtworek.backend.common.web.OutboundHttpClientConfig;

import java.time.Clock;

@SpringBootApplication
@EnableScheduling
@Import({CommonWebConfig.class, GlobalExceptionHandler.class, CorrelationWebClientConfig.class, OutboundHttpClientConfig.class})
public class OrderServiceApplication {
    @Bean
    Clock orderClock() {
        return Clock.systemUTC();
    }
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
