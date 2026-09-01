package pl.jakubtworek.backend_engineering.stage_1.block_c.bean;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class BeanLifecycleContractTest {

    @Test
    void shouldExposeTheInitializationAndDestructionOrder() {
        LifecycleEventLog events = new LifecycleEventLog();
        var context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().addBeanPostProcessor(new LifecycleObservationPostProcessor(events));
        context.registerBean(LifecycleEventLog.class, () -> events);
        context.register(LifecycleConfiguration.class);
        context.refresh();
        try (context) {

            assertThat(context.getBean(LifecycleProbe.class).use()).isEqualTo("injected");
            assertThat(events.snapshot()).containsExactly(
                    "constructor",
                    "dependency-injection",
                    "bean-name-aware",
                    "before-initialization",
                    "post-construct",
                    "after-properties-set",
                    "custom-init",
                    "after-initialization",
                    "ready-to-use"
            );
        }

        assertThat(events.snapshot()).endsWith(
                "pre-destroy",
                "disposable-bean-destroy",
                "custom-destroy"
        );
    }

    @Configuration(proxyBeanMethods = false)
    static class LifecycleConfiguration {

        @Bean
        LifecycleProbe.ProbeDependency probeDependency() {
            return new LifecycleProbe.ProbeDependency("injected");
        }

        @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
        LifecycleProbe lifecycleProbe(LifecycleEventLog events) {
            return new LifecycleProbe(events);
        }

    }
}
