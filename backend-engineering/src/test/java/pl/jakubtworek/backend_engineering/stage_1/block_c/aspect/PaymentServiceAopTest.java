package pl.jakubtworek.backend_engineering.stage_1.block_c.aspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple integration test for AOP.
 *
 * AOP is usually tested through integration behavior,
 * not by testing aspects directly.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
public class PaymentServiceAopTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ExternalApiService externalApiService;

    @Test
    void serviceCallMustCrossTheProxyAndExecuteOrderedAdvice(CapturedOutput output) {
        String result = paymentService.processPayment(1L);

        assertThat(AopUtils.isAopProxy(paymentService)).isTrue();
        assertThat(result).isEqualTo("PAYMENT_SUCCESS");
        assertThat(output)
                .contains("[SECURITY] Authorization check passed")
                .contains("[LOG BEFORE] Method called:")
                .contains("[PERFORMANCE]")
                .contains("[LOG AFTER RETURNING]");
    }

    @Test
    void retryAnnotationMustInvokeTheTargetUntilTheThirdAttempt(CapturedOutput output) {
        assertThat(externalApiService.callExternalApi()).isEqualTo("EXTERNAL_API_SUCCESS");
        assertThat(output)
                .contains("[RETRY] Attempt: 1")
                .contains("[RETRY] Attempt: 2")
                .contains("[RETRY] Attempt: 3")
                .contains("External API failure on attempt: 1")
                .contains("External API failure on attempt: 2");
    }
}
