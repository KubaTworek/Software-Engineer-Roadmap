package pl.jakubtworek.booking.service.pitfall;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class BeanLifecyclePitfallService {
    private final ObjectProvider<BeanLifecycleProbe> probeProvider;

    public BeanLifecyclePitfallService(ObjectProvider<BeanLifecycleProbe> probeProvider) {
        this.probeProvider = probeProvider;
    }

    public BeanLifecycleView inspectSingletonBean() {
        BeanLifecycleProbe first = probeProvider.getObject();
        BeanLifecycleProbe second = probeProvider.getObject();
        return new BeanLifecycleView(
                first.getInstanceId(),
                second.getInstanceId(),
                first == second,
                first.isPostConstructCalled(),
                first.isAfterPropertiesSetCalled(),
                first.isPreDestroyCalled()
        );
    }
}
