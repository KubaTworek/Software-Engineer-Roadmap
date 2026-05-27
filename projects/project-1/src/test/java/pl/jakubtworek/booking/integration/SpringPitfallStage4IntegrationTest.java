package pl.jakubtworek.booking.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.jakubtworek.booking.aop.MeasurementRegistry;
import pl.jakubtworek.booking.service.pitfall.BeanLifecyclePitfallService;
import pl.jakubtworek.booking.service.pitfall.BeanLifecycleView;
import pl.jakubtworek.booking.service.pitfall.MeasuredPitfallService;
import pl.jakubtworek.booking.service.pitfall.SelfInvocationPitfallService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SpringPitfallStage4IntegrationTest {
    @Autowired
    SelfInvocationPitfallService selfInvocationPitfallService;

    @Autowired
    MeasuredPitfallService measuredPitfallService;

    @Autowired
    MeasurementRegistry measurementRegistry;

    @Autowired
    BeanLifecyclePitfallService beanLifecyclePitfallService;

    @Test
    void selfInvocationBypassesTransactionalProxy() {
        boolean activeWhenCalledThroughThis = selfInvocationPitfallService.callTransactionalMethodThroughThis();
        boolean activeWhenCalledThroughProxy = selfInvocationPitfallService.callTransactionalMethodThroughProxy();

        assertThat(activeWhenCalledThroughThis)
                .as("@Transactional is not applied when the method is called with this.transactionalMethod()")
                .isFalse();
        assertThat(activeWhenCalledThroughProxy)
                .as("@Transactional is applied when the call goes through the Spring proxy")
                .isTrue();
    }

    @Test
    void selfInvocationBypassesMeasuredAspect() {
        measurementRegistry.clear();

        String result = measuredPitfallService.callMeasuredMethodThroughThis("spring");

        assertThat(result).isEqualTo("SPRING");
        assertThat(measurementRegistry.findAll())
                .as("@Measured is not intercepted when the annotated method is called through this")
                .isEmpty();
    }

    @Test
    void proxyInvocationTriggersMeasuredAspect() {
        measurementRegistry.clear();

        String result = measuredPitfallService.callMeasuredMethodThroughProxy("spring");

        assertThat(result).isEqualTo("SPRING");
        assertThat(measurementRegistry.findAll())
                .hasSize(1)
                .first()
                .satisfies(call -> {
                    assertThat(call.methodName()).contains("MeasuredPitfallService.measuredOperation");
                    assertThat(call.label()).isEqualTo("spring-pitfall-measured-operation");
                    assertThat(call.durationNanos()).isGreaterThanOrEqualTo(0L);
                });
    }

    @Test
    void singletonBeanLifecycleIsVisibleAfterContextStarts() {
        BeanLifecycleView view = beanLifecyclePitfallService.inspectSingletonBean();

        assertThat(view.sameSingletonInstance()).isTrue();
        assertThat(view.firstLookupInstanceId()).isEqualTo(view.secondLookupInstanceId());
        assertThat(view.postConstructCalled()).isTrue();
        assertThat(view.afterPropertiesSetCalled()).isTrue();
        assertThat(view.preDestroyCalled()).isFalse();
    }
}
