package pl.jakubtworek.backend_systems_lab_stage_1.block_c.aspect;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Simple integration test for AOP.
 *
 * AOP is usually tested through integration behavior,
 * not by testing aspects directly.
 */
@SpringBootTest
public class PaymentServiceAopTest {

    @Autowired
    private PaymentService paymentService;

    @Test
    void shouldExecuteAspectLogic() {

        /**
         * During this call:
         * - logging aspect executes,
         * - performance aspect executes,
         * - transaction aspect executes.
         */
        paymentService.processPayment(1L);
    }
}