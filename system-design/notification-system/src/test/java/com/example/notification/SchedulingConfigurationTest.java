package com.example.notification;

import com.example.notification.config.SchedulingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfiguration.class);

    @Test void runtimeRegistersSchedulingByDefault() {
        context.run(application -> assertThat(application)
                .hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test void manualPipelineModeDoesNotRegisterScheduledCallbacks() {
        context.withPropertyValues("notification.scheduling.enabled=false")
                .run(application -> assertThat(application)
                        .doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
