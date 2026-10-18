package com.example.ecommerce.payment;

import com.example.ecommerce.monitoring.BusinessMetrics;
import com.example.ecommerce.payment.dto.PaymentDtos;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PaymentReconciliationWorkerTest {
    @Test
    void unknownProviderResultDoesNotInventFailureButTerminalResultIsApplied() {
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentProviderClient provider = mock(PaymentProviderClient.class);
        PaymentService service = mock(PaymentService.class);
        BusinessMetrics metrics = mock(BusinessMetrics.class);
        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(7L);
        when(payment.getAmount()).thenReturn(new BigDecimal("49.99"));
        when(payment.getCurrency()).thenReturn("PLN");
        when(payments.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(PaymentStatus.PENDING), any())).thenReturn(List.of(payment));
        when(provider.lookup(payment)).thenReturn(Optional.empty());
        var worker = new PaymentReconciliationWorker(payments, provider, service,
                Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC), metrics);

        worker.reconcileUnknownPayments();
        verifyNoInteractions(service);
        verify(metrics).paymentReconciliationStillUnknown();

        when(provider.lookup(payment)).thenReturn(Optional.of(
                new PaymentProviderClient.ProviderResult("reconcile-7", "psp-7", PaymentStatus.SUCCEEDED)));
        worker.reconcileUnknownPayments();

        ArgumentCaptor<PaymentDtos.ProviderWebhookEvent> event =
                ArgumentCaptor.forClass(PaymentDtos.ProviderWebhookEvent.class);
        verify(service).handleProviderEvent(event.capture());
        verify(metrics).paymentReconciliationResolved();
        assertThat(event.getValue().eventId()).isEqualTo("reconcile-7");
        assertThat(event.getValue().status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }
}
