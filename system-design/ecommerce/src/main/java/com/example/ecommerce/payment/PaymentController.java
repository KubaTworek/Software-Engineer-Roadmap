package com.example.ecommerce.payment;

import com.example.ecommerce.payment.dto.PaymentDtos;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/{paymentId}/mock-success")
    public PaymentDtos.PaymentResponse mockSuccess(@PathVariable Long paymentId) {
        return payments.mockSuccess(paymentId);
    }

    @PostMapping("/{paymentId}/mock-failure")
    public PaymentDtos.PaymentResponse mockFailure(@PathVariable Long paymentId) {
        return payments.mockFailure(paymentId);
    }
}
