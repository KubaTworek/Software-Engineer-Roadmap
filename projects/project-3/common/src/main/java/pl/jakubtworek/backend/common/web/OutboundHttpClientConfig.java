package pl.jakubtworek.backend.common.web;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Wspólna konfiguracja outbound HTTP clienta używanego przez WebClient.
 *
 * Ta klasa ustawia techniczne timeouty dla połączeń HTTP wychodzących z serwisów.
 * Dzięki temu wywołania service-to-service nie wiszą bez końca, gdy downstream:
 *
 * - nie odpowiada,
 * - odpowiada bardzo wolno,
 * - ma problem z siecią,
 * - nie akceptuje połączeń.
 *
 * To jest jeden z podstawowych elementów resilience. Bez timeoutów awaria jednego
 * downstreamu może szybko zablokować wątki/connection pool w serwisie wywołującym.
 */
@Configuration
public class OutboundHttpClientConfig {

    /**
     * Customizer aplikowany do każdego WebClient.Builder tworzonego przez Spring Boot.
     *
     * Dzięki temu timeouty są ustawione centralnie w module common, zamiast powtarzać
     * tę samą konfigurację w każdym mikroserwisie.
     *
     * Wartości można nadpisać konfiguracją:
     *
     * app.http-client.connect-timeout-ms
     * app.http-client.response-timeout-ms
     */
    @Bean
    WebClientCustomizer outboundHttpClientTimeoutCustomizer(
            @Value("${app.http-client.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${app.http-client.response-timeout-ms:2500}") long responseTimeoutMs
    ) {
        return builder -> {
            /*
             * Reactor Netty HttpClient jest niskopoziomowym klientem HTTP,
             * którego użyje WebClient pod spodem.
             */
            HttpClient httpClient = HttpClient.create()

                    /*
                     * CONNECT_TIMEOUT_MILLIS określa maksymalny czas na zestawienie połączenia TCP.
                     *
                     * Przykład:
                     * Jeśli reservation-service próbuje połączyć się z catalog-service,
                     * ale host/port jest niedostępny, połączenie nie powinno wisieć długo.
                     *
                     * Domyślnie: 1000 ms.
                     */
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)

                    /*
                     * responseTimeout określa maksymalny czas oczekiwania na odpowiedź HTTP.
                     *
                     * To chroni przed sytuacją, w której połączenie zostało zestawione,
                     * ale downstream odpowiada zbyt wolno albo wcale.
                     *
                     * Domyślnie: 2500 ms.
                     */
                    .responseTimeout(Duration.ofMillis(responseTimeoutMs));

            /*
             * Podpinamy skonfigurowany Reactor Netty HttpClient jako connector WebClienta.
             *
             * Od tego momentu WebClient.Builder używany w serwisach będzie dziedziczył
             * te ustawienia timeoutów.
             */
            builder.clientConnector(new ReactorClientHttpConnector(httpClient));
        };
    }
}