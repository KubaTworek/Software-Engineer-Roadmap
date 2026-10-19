package com.example.notification.infrastructure.provider;

import com.example.notification.application.Ports;
import com.example.notification.domain.Channel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Profil wykonywalnej granicy sieciowej. Domyślne laboratorium używa providerów
 * deterministycznych; `http-provider` przeprowadza ten sam pipeline przez HTTP.
 */
@Configuration
@Profile("http-provider")
public class HttpProviderConfiguration {
    @Bean HttpClient providerHttpClient(@Value("${notification.provider.connect-timeout-ms:500}") long timeout) {
        return HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout)).build();
    }

    @Bean @Order(1) Ports.NotificationProvider emailHttpProvider(HttpClient client,
            @Value("${notification.provider.email-url}") URI endpoint,
            @Value("${notification.provider.request-timeout-ms:1000}") long timeout) {
        return new HttpNotificationProvider(Channel.EMAIL, "email-http-primary", endpoint, client,
                Duration.ofMillis(timeout));
    }

    @Bean @Order(1) Ports.NotificationProvider smsHttpProvider(HttpClient client,
            @Value("${notification.provider.sms-url}") URI endpoint,
            @Value("${notification.provider.request-timeout-ms:1000}") long timeout) {
        return new HttpNotificationProvider(Channel.SMS, "sms-http-primary", endpoint, client,
                Duration.ofMillis(timeout));
    }

    @Bean @Order(1) Ports.NotificationProvider pushHttpProvider(HttpClient client,
            @Value("${notification.provider.push-url}") URI endpoint,
            @Value("${notification.provider.request-timeout-ms:1000}") long timeout) {
        return new HttpNotificationProvider(Channel.PUSH, "push-http-primary", endpoint, client,
                Duration.ofMillis(timeout));
    }

    @Bean @Order(1) Ports.NotificationProvider inAppHttpProvider(HttpClient client,
            @Value("${notification.provider.in-app-url}") URI endpoint,
            @Value("${notification.provider.request-timeout-ms:1000}") long timeout) {
        return new HttpNotificationProvider(Channel.IN_APP, "in-app-http-primary", endpoint, client,
                Duration.ofMillis(timeout));
    }
}
