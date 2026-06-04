package pl.jakubtworek.backend.common.web;

import org.slf4j.MDC;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;

@Configuration
public class CorrelationWebClientConfig {
    @Bean
    WebClientCustomizer correlationWebClientCustomizer() {
        return builder -> builder.filter((request, next) -> {
            ClientRequest.Builder mutated = ClientRequest.from(request);
            String correlationId = MDC.get(CorrelationId.MDC_CORRELATION_ID);
            String requestId = MDC.get(CorrelationId.MDC_REQUEST_ID);

            if (correlationId != null && !correlationId.isBlank()) {
                mutated.header(CorrelationId.CORRELATION_ID_HEADER, correlationId);
            }
            if (requestId != null && !requestId.isBlank()) {
                mutated.header(CorrelationId.REQUEST_ID_HEADER, requestId);
            }

            return next.exchange(mutated.build());
        });
    }
}
