package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/** Bean rejestrujący obserwowalne fazy własnego cyklu życia. */
public final class LifecycleProbe implements BeanNameAware, InitializingBean, DisposableBean {

    private final LifecycleEventLog events;
    private ProbeDependency dependency;

    public LifecycleProbe(LifecycleEventLog events) {
        this.events = events;
        events.record("constructor");
    }

    @Autowired
    void injectDependency(ProbeDependency dependency) {
        this.dependency = dependency;
        events.record("dependency-injection");
    }

    @Override
    public void setBeanName(String name) {
        events.record("bean-name-aware");
    }

    @PostConstruct
    void postConstruct() {
        events.record("post-construct");
    }

    @Override
    public void afterPropertiesSet() {
        events.record("after-properties-set");
    }

    public void customInit() {
        events.record("custom-init");
    }

    public String use() {
        events.record("ready-to-use");
        return dependency.value();
    }

    @PreDestroy
    void preDestroy() {
        events.record("pre-destroy");
    }

    @Override
    public void destroy() {
        events.record("disposable-bean-destroy");
    }

    public void customDestroy() {
        events.record("custom-destroy");
    }

    public record ProbeDependency(String value) {
    }
}
