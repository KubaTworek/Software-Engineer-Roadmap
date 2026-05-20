package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Executes application logic after Spring startup.
 */
@Component
public class DemoRunner implements CommandLineRunner {

    private final PaymentService paymentService;

    public DemoRunner(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Override
    public void run(String... args) {

        /**
         * Prints generated proxy class.
         *
         * JDK Proxy example:
         * class jdk.proxy2.$Proxy52
         *
         * CGLIB example:
         * PaymentServiceImpl$$SpringCGLIB$$0
         */
        System.out.println(paymentService.getClass());

        System.out.println("\n=== EXTERNAL CALL ===");

        /**
         * Goes THROUGH proxy.
         *
         * Transaction + Aspect will work.
         */
        paymentService.pay();

        System.out.println("\n=== INTERNAL CALL ===");

        /**
         * Demonstrates self invocation issue.
         *
         * internalCall() -> this.pay()
         *
         * pay() bypasses proxy.
         */
        paymentService.internalCall();
    }
}