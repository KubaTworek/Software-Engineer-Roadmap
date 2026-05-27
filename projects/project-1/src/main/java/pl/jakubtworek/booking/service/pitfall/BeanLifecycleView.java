package pl.jakubtworek.booking.service.pitfall;

import java.util.UUID;

public record BeanLifecycleView(
        UUID firstLookupInstanceId,
        UUID secondLookupInstanceId,
        boolean sameSingletonInstance,
        boolean postConstructCalled,
        boolean afterPropertiesSetCalled,
        boolean preDestroyCalled
) {
}
