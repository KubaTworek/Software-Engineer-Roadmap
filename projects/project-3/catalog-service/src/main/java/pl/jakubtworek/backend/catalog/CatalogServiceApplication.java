package pl.jakubtworek.backend.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import pl.jakubtworek.backend.common.web.CommonWebConfig;
import pl.jakubtworek.backend.common.web.GlobalExceptionHandler;
import pl.jakubtworek.backend.common.web.CorrelationWebClientConfig;
import pl.jakubtworek.backend.common.web.OutboundHttpClientConfig;

@SpringBootApplication
@Import({CommonWebConfig.class, GlobalExceptionHandler.class, CorrelationWebClientConfig.class, OutboundHttpClientConfig.class})
public class CatalogServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
