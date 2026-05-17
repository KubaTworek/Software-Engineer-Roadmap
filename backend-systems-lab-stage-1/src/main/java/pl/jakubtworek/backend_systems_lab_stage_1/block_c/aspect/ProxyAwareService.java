package pl.jakubtworek.backend_systems_lab_stage_1.block_c.aspect;

import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates workaround for self-invocation.
 *
 * Normally self invocation bypasses proxy.
 * AopContext.currentProxy() forces proxy usage.
 */
@Service
public class ProxyAwareService {

    /**
     * Transactional method.
     */
    @Transactional
    public void transactionalMethod() {

        System.out.println(
                "Transactional method executed"
        );
    }

    /**
     * Uses proxy explicitly.
     */
    public void callThroughProxy() {

        /**
         * This call goes through proxy.
         *
         * Transaction and aspects will work correctly.
         */
        ((ProxyAwareService) AopContext.currentProxy())
                .transactionalMethod();
    }
}