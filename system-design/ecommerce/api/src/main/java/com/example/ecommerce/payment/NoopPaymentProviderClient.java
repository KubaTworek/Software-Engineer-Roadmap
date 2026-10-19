package com.example.ecommerce.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Symulacja: lokalne laboratorium nie deklaruje połączenia z prawdziwym PSP. */
@Component
@ConditionalOnMissingBean(PaymentProviderClient.class)
public class NoopPaymentProviderClient implements PaymentProviderClient {
    @Override
    public Optional<ProviderResult> lookup(Payment payment) {
        return Optional.empty();
    }
}
