package pl.jakubtworek.backend_systems_lab_stage_1.block_c.aspect;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service used for AOP demonstrations.
 *
 * Spring creates a proxy for this bean
 * because aspects match its methods.
 */
@Service
public class PaymentService {

    /**
     * Method intercepted by aspects.
     */
    @Transactional
    public String processPayment(Long orderId) {

        System.out.println("Processing payment for order: " + orderId);

        return "PAYMENT_SUCCESS";
    }

    /**
     * Demonstrates exception handling in AOP.
     */
    public void processFailedPayment() {

        throw new IllegalStateException("Payment processing failed");
    }

    /**
     * Demonstrates self-invocation problem.
     *
     * Internal call bypasses proxy.
     */
    public void internalCall() {

        /**
         * This call does NOT go through Spring proxy.
         *
         * Aspects like:
         * - @Transactional
         * - @Cacheable
         * - custom AOP aspects
         *
         * may not work correctly.
         */
        processPayment(1L);
    }
}