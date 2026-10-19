package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * BeanPostProcessor allows modifying beans
 * before and after initialization.
 *
 * Spring internally uses BeanPostProcessor
 * to create proxies for:
 * - @Transactional
 * - @Async
 * - Security
 * - AOP
 */
@Component
public class CustomBeanPostProcessor implements BeanPostProcessor {

    private static final String DEMO_PACKAGE =
            "pl.jakubtworek.backend_engineering.stage_1.block_c.bean";

    /**
     * Called BEFORE initialization callbacks.
     */
    @Override
    public Object postProcessBeforeInitialization(
            Object bean,
            String beanName
    ) throws BeansException {

        if (belongsToBeanLifecycleDemo(bean)) {
            System.out.println("Before Initialization: " + beanName);
        }

        return bean;
    }

    /**
     * Called AFTER initialization callbacks.
     *
     * This is usually where Spring wraps
     * the bean with a proxy.
     */
    @Override
    public Object postProcessAfterInitialization(
            Object bean,
            String beanName
    ) throws BeansException {

        if (belongsToBeanLifecycleDemo(bean)) {
            System.out.println("After Initialization: " + beanName);
        }

        return bean;
    }

    private boolean belongsToBeanLifecycleDemo(Object bean) {
        return bean.getClass().getPackageName().startsWith(DEMO_PACKAGE);
    }
}
