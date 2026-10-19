package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyMechanicsTest {

    @Test
    void shouldCreateAJdkProxyWhenAnInterfaceIsSelectedExplicitly() {
        AtomicInteger interceptedCalls = new AtomicInteger();
        ProxyFactory factory = proxyFactory(new PaymentServiceImpl(), interceptedCalls);
        factory.setInterfaces(PaymentService.class);
        factory.setProxyTargetClass(false);

        PaymentService proxy = (PaymentService) factory.getProxy();
        proxy.pay();

        assertThat(AopUtils.isJdkDynamicProxy(proxy)).isTrue();
        assertThat(interceptedCalls).hasValue(1);
    }

    @Test
    void shouldCreateACglibProxyWhenClassBasedProxyingIsSelected() {
        AtomicInteger interceptedCalls = new AtomicInteger();
        ProxyFactory factory = proxyFactory(new CglibExampleService(), interceptedCalls);
        factory.setProxyTargetClass(true);

        CglibExampleService proxy = (CglibExampleService) factory.getProxy();
        proxy.execute();

        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();
        assertThat(interceptedCalls).hasValue(1);
    }

    @Test
    void shouldShowThatSelfInvocationDoesNotReenterTheProxy() {
        AtomicInteger interceptedCalls = new AtomicInteger();
        ProxyFactory factory = proxyFactory(new PaymentServiceImpl(), interceptedCalls);
        factory.setInterfaces(PaymentService.class);

        PaymentService proxy = (PaymentService) factory.getProxy();
        proxy.internalCall();

        assertThat(interceptedCalls)
                .as("internalCall is intercepted, but this.pay() is not")
                .hasValue(1);
    }

    @Test
    void shouldShowThatACglibProxyCannotAdviseAFinalMethod() {
        AtomicInteger interceptedCalls = new AtomicInteger();
        ProxyFactory factory = proxyFactory(new FinalService(), interceptedCalls);
        factory.setProxyTargetClass(true);

        FinalService proxy = (FinalService) factory.getProxy();
        proxy.test();

        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();
        assertThat(interceptedCalls).hasValue(0);
    }

    private static ProxyFactory proxyFactory(Object target, AtomicInteger interceptedCalls) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice((MethodInterceptor) invocation -> {
            interceptedCalls.incrementAndGet();
            return invocation.proceed();
        });
        return factory;
    }
}
