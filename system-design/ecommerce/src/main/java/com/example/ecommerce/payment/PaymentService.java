package com.example.ecommerce.payment;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.inventory.InventoryService;
import com.example.ecommerce.notification.NotificationService;
import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.payment.dto.PaymentDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final InventoryService inventory;
    private final NotificationService notifications;

    public PaymentService(
            PaymentRepository payments,
            InventoryService inventory,
            NotificationService notifications
    ) {
        this.payments = payments;
        this.inventory = inventory;
        this.notifications = notifications;
    }

    @Transactional
    public Payment createPayment(CustomerOrder order, String idempotencyKey) {
        return payments.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> payments.save(new Payment(
                        order,
                        "MOCK_PROVIDER",
                        order.getTotalAmount(),
                        order.getCurrency(),
                        idempotencyKey
                )));
    }

    @Transactional
    public PaymentDtos.PaymentResponse mockSuccess(Long paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return toResponse(payment);
        }

        payment.markSucceeded("mock_" + UUID.randomUUID());
        payment.getOrder().markPaid();
        inventory.confirmReservations(payment.getOrder().getId());
        notifications.sendOrderConfirmation(payment.getOrder());

        return toResponse(payment);
    }

    @Transactional
    public PaymentDtos.PaymentResponse mockFailure(Long paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found"));

        if (payment.getStatus() == PaymentStatus.FAILED) {
            return toResponse(payment);
        }

        payment.markFailed("mock_" + UUID.randomUUID());
        payment.getOrder().markPaymentFailed();
        inventory.releaseReservations(payment.getOrder().getId());

        return toResponse(payment);
    }

    public PaymentDtos.PaymentResponse toResponse(Payment payment) {
        return new PaymentDtos.PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getProvider(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency()
        );
    }
}
