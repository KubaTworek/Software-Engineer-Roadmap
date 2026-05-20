package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates the self-invocation problem.
 *
 * @Transactional works through Spring proxy.
 * Internal method calls using this.method()
 * bypass the proxy.
 */
@Service
public class SelfInvocationService {

    /**
     * This method is transactional only when called from outside
     * through the Spring proxy.
     */
    @Transactional
    public void transactionalMethod() {
        System.out.println("Transactional method executed");
    }

    /**
     * This method calls another method from the same class.
     *
     * The call is equivalent to this.transactionalMethod(),
     * so Spring proxy is bypassed.
     */
    public void nonTransactionalMethod() {

        /**
         * Transaction will NOT start here.
         */
        transactionalMethod();
    }
}