package pl.jakubtworek.backend.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import pl.jakubtworek.backend.common.web.CommonWebConfig;
import pl.jakubtworek.backend.common.web.GlobalExceptionHandler;
import pl.jakubtworek.backend.common.web.CorrelationWebClientConfig;
import pl.jakubtworek.backend.common.web.OutboundHttpClientConfig;
import org.springframework.context.annotation.Bean;
import java.time.Clock;

@SpringBootApplication
@Import({CommonWebConfig.class, GlobalExceptionHandler.class, CorrelationWebClientConfig.class, OutboundHttpClientConfig.class})
public class PaymentMockServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentMockServiceApplication.class, args);
    }

    @Bean
    Clock paymentClock() { return Clock.systemUTC(); }
}
