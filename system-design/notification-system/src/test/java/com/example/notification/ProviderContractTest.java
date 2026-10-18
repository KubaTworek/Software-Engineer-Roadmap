package com.example.notification;

import com.example.notification.application.Exceptions;
import com.example.notification.application.Ports;
import com.example.notification.domain.RenderedNotification;
import com.example.notification.infrastructure.provider.SimulatedProviders;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/** Ten sam kontrakt powinien przejść każdy adapter zewnętrznego providera. */
class ProviderContractTest {
    private final List<Ports.NotificationProvider> providers = List.of(
            new SimulatedProviders.EmailPrimary(), new SimulatedProviders.EmailFallback(),
            new SimulatedProviders.SmsPrimary(), new SimulatedProviders.SmsFallback(),
            new SimulatedProviders.Push(), new SimulatedProviders.InApp());
    private final RenderedNotification message = new RenderedNotification("subject", "body");

    @Test void retryWithTheSameOperationKeyReturnsStableProviderMessageId() {
        for (var provider : providers) {
            var first = provider.send("tenant", "recipient@example.com", message, "operation-1");
            var second = provider.send("tenant", "recipient@example.com", message, "operation-1");
            assertThat(second.providerMessageId()).as(provider.providerName()).isEqualTo(first.providerMessageId());
        }
    }

    @Test void adaptersClassifyFailureCertaintyConsistently() {
        for (var provider : providers) {
            assertThatThrownBy(() -> provider.send("t", "temporary-failure", message, "k"))
                    .isInstanceOfSatisfying(Exceptions.ProviderCallException.class,
                            ex -> assertThat(ex.certainty()).isEqualTo(Exceptions.OutcomeCertainty.NOT_SENT));
            assertThatThrownBy(() -> provider.send("t", "unknown-outcome", message, "k"))
                    .isInstanceOfSatisfying(Exceptions.ProviderCallException.class,
                            ex -> assertThat(ex.certainty()).isEqualTo(Exceptions.OutcomeCertainty.UNKNOWN));
            assertThatThrownBy(() -> provider.send("t", "permanent-failure", message, "k"))
                    .isInstanceOf(Exceptions.PermanentProviderException.class);
        }
    }
}
