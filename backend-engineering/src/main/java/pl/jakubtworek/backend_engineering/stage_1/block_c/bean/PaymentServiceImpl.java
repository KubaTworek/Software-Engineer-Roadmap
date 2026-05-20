package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring will create a proxy for this bean
 * because of @Transactional.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    /**
     * Method intercepted by proxy.
     */
    @Override
    @Transactional
    public void pay() {

        System.out.println("Payment executed");
    }

    /**
     * Demonstrates self invocation problem.
     */
    @Override
    public void internalCall() {

        System.out.println("Calling pay() internally");

        /**
         * IMPORTANT:
         * This call bypasses the proxy.
         *
         * Transaction will NOT start because:
         * this.pay() -> direct call
         * instead of:
         * proxy.pay()
         */
        pay();
    }
}