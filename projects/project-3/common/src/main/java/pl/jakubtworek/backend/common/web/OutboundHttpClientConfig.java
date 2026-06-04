package pl.jakubtworek.backend.common.web;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OutboundHttpClientConfig {
    @Bean
    WebClientCustomizer outboundHttpClientTimeoutCustomizer(
            @Value("${app.http-client.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${app.http-client.response-timeout-ms:2500}") long responseTimeoutMs
    ) {
        return builder -> {
            HttpClient httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                    .responseTimeout(Duration.ofMillis(responseTimeoutMs));
            builder.clientConnector(new ReactorClientHttpConnector(httpClient));
        };
    }
}
