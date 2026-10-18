package com.example.notification.infrastructure.provider;

import com.example.notification.application.Exceptions;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Produkcyjna granica adaptera HTTP. Klasyfikacja błędu jest częścią kontraktu:
 * odpowiedź 429/5xx dowodzi odrzucenia requestu, ale timeout po wysłaniu bajtów
 * pozostawia efekt UNKNOWN i zabrania automatycznego fallbacku.
 */
public final class HttpNotificationProvider implements Ports.NotificationProvider {
    private final Channel channel;
    private final String name;
    private final URI endpoint;
    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpNotificationProvider(Channel channel, String name, URI endpoint,
                                    HttpClient client, Duration requestTimeout) {
        this.channel = channel; this.name = name; this.endpoint = endpoint;
        this.client = client; this.requestTimeout = requestTimeout;
    }

    @Override public Channel channel() { return channel; }
    @Override public String providerName() { return name; }

    @Override
    public ProviderSendResult send(String tenantId, String recipient, RenderedNotification notification,
                                   String idempotencyKey) {
        String body = "{\"recipient\":\"" + escape(recipient) + "\",\"subject\":\""
                + escape(notification.subject()) + "\",\"body\":\"" + escape(notification.body()) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Tenant-Id", tenantId)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                String providerId = response.headers().firstValue("X-Provider-Message-Id")
                        .orElseThrow(() -> new Exceptions.ProviderCallException(
                                "Provider accepted without operation id", Exceptions.OutcomeCertainty.UNKNOWN));
                return new ProviderSendResult(name, providerId);
            }
            if (response.statusCode() == 429 || response.statusCode() >= 500) {
                throw new Exceptions.ProviderCallException(
                        "Provider rejected request with HTTP " + response.statusCode(),
                        Exceptions.OutcomeCertainty.NOT_SENT);
            }
            throw new Exceptions.PermanentProviderException("Provider rejected request with HTTP " + response.statusCode());
        } catch (HttpTimeoutException timeout) {
            throw new Exceptions.ProviderCallException("Provider response timeout", Exceptions.OutcomeCertainty.UNKNOWN);
        } catch (ConnectException connect) {
            throw new Exceptions.ProviderCallException("Provider connection failed", Exceptions.OutcomeCertainty.NOT_SENT);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new Exceptions.ProviderCallException("Provider call interrupted", Exceptions.OutcomeCertainty.UNKNOWN);
        } catch (java.io.IOException transport) {
            throw new Exceptions.ProviderCallException("Provider transport failed", Exceptions.OutcomeCertainty.UNKNOWN);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
