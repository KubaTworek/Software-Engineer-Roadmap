package pl.jakubtworek.backend_systems_lab_stage_1.block_c.bean;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Demonstrates the lifecycle of a Spring Bean.
 *
 * Order:
 * 1. Constructor
 * 2. Dependency Injection
 * 3. @PostConstruct
 * 4. afterPropertiesSet()
 * 5. Bean ready to use
 * 6. @PreDestroy
 * 7. destroy()
 */
@Component
public class LifecycleBean implements InitializingBean, DisposableBean {

    public LifecycleBean() {
        System.out.println("1. Constructor called");
    }

    /**
     * Executed after dependency injection.
     */
    @PostConstruct
    public void postConstruct() {
        System.out.println("2. @PostConstruct executed");
    }

    /**
     * Spring lifecycle callback.
     */
    @Override
    public void afterPropertiesSet() {
        System.out.println("3. afterPropertiesSet executed");
    }

    /**
     * Executed before bean destruction.
     */
    @PreDestroy
    public void preDestroy() {
        System.out.println("6. @PreDestroy executed");
    }

    @Override
    public void destroy() {
        System.out.println("7. destroy executed");
    }
}