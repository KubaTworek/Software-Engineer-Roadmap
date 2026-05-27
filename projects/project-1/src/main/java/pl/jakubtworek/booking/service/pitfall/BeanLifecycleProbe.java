package pl.jakubtworek.booking.service.pitfall;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class BeanLifecycleProbe implements InitializingBean {
    private final UUID instanceId = UUID.randomUUID();
    private final AtomicBoolean postConstructCalled = new AtomicBoolean(false);
    private final AtomicBoolean afterPropertiesSetCalled = new AtomicBoolean(false);
    private final AtomicBoolean preDestroyCalled = new AtomicBoolean(false);

    @PostConstruct
    void postConstruct() {
        postConstructCalled.set(true);
    }

    @Override
    public void afterPropertiesSet() {
        afterPropertiesSetCalled.set(true);
    }

    @PreDestroy
    void preDestroy() {
        preDestroyCalled.set(true);
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public boolean isPostConstructCalled() {
        return postConstructCalled.get();
    }

    public boolean isAfterPropertiesSetCalled() {
        return afterPropertiesSetCalled.get();
    }

    public boolean isPreDestroyCalled() {
        return preDestroyCalled.get();
    }
}
