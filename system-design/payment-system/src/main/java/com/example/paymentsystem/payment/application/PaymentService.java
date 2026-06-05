package com.example.paymentsystem.payment.application;

import com.example.paymentsystem.payment.api.CreatePaymentRequest;
import com.example.paymentsystem.payment.api.PaymentResponse;
import com.example.paymentsystem.payment.domain.Payment;
import com.example.paymentsystem.payment.domain.PaymentRepository;
import com.example.paymentsystem.payment.infrastructure.psp.PaymentProviderClient;
import com.example.paymentsystem.payment.infrastructure.psp.PspPaymentRequest;
import com.example.paymentsystem.payment.infrastructure.psp.PspPaymentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProviderClient paymentProviderClient;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentProviderClient paymentProviderClient
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentProviderClient = paymentProviderClient;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        Payment payment = Payment.create(
                request.orderId(),
                request.customerId(),
                request.amount(),
                request.currency()
        );

        paymentRepository.save(payment);

        PspPaymentResponse pspResponse = paymentProviderClient.createPayment(
                new PspPaymentRequest(
                        payment.getPaymentId(),
                        payment.getOrderId(),
                        payment.getAmount(),
                        payment.getCurrency()
                )
        );

        payment.markAsPending(
                pspResponse.providerPaymentId(),
                pspResponse.checkoutUrl()
        );

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        return PaymentResponse.from(payment);
    }
}
