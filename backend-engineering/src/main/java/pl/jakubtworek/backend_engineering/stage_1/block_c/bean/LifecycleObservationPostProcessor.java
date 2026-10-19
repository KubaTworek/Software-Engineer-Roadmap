package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

/** Obserwuje granice łańcucha inicjalizacyjnego dla jednego beana demonstracyjnego. */
public final class LifecycleObservationPostProcessor implements BeanPostProcessor, PriorityOrdered {

    private final LifecycleEventLog events;

    public LifecycleObservationPostProcessor(LifecycleEventLog events) {
        this.events = events;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof LifecycleProbe) {
            events.record("before-initialization");
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof LifecycleProbe) {
            events.record("after-initialization");
        }
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
