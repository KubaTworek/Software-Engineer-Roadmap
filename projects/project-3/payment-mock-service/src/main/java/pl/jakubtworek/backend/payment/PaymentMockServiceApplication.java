package pl.jakubtworek.backend.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import pl.jakubtworek.backend.common.web.CommonWebConfig;
import pl.jakubtworek.backend.common.web.GlobalExceptionHandler;
import pl.jakubtworek.backend.common.web.CorrelationWebClientConfig;

@SpringBootApplication
@Import({CommonWebConfig.class, GlobalExceptionHandler.class, CorrelationWebClientConfig.class})
public class PaymentMockServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentMockServiceApplication.class, args);
    }
}
