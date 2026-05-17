package pl.jakubtworek.backend_systems_lab_stage_1.block_c.aspect;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Demonstrates AOP behavior during application startup.
 */
@Component
public class AopDemoRunner implements CommandLineRunner {

    private final PaymentService paymentService;
    private final ExternalApiService externalApiService;
    private final ProductCacheService productCacheService;
    private final ProxyAwareService proxyAwareService;

    public AopDemoRunner(
            PaymentService paymentService,
            ExternalApiService externalApiService,
            ProductCacheService productCacheService,
            ProxyAwareService proxyAwareService
    ) {
        this.paymentService = paymentService;
        this.externalApiService = externalApiService;
        this.productCacheService = productCacheService;
        this.proxyAwareService = proxyAwareService;
    }

    @Override
    public void run(String... args) {

        /**
         * Demonstrates:
         * - @Before
         * - @AfterReturning
         * - @Around
         * - execution time measurement
         */
        paymentService.processPayment(1L);

        /**
         * Demonstrates exception interception.
         */
        try {
            paymentService.processFailedPayment();
        } catch (Exception ignored) {
        }

        /**
         * Demonstrates retry aspect.
         */
        externalApiService.callExternalApi();

        /**
         * Demonstrates cache aspect.
         *
         * First call loads from database.
         * Second call returns cached value.
         */
        productCacheService.getProduct(1L);
        productCacheService.getProduct(1L);

        /**
         * Demonstrates proxy-aware self invocation workaround.
         */
        proxyAwareService.callThroughProxy();

        /**
         * Prints proxy type.
         */
        System.out.println(
                "Proxy class: "
                        + paymentService.getClass()
        );
    }
}