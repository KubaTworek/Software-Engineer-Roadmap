package pl.jakubtworek.booking.service.pitfall;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import pl.jakubtworek.booking.aop.Measured;

@Service
public class MeasuredPitfallService {
    private final ObjectProvider<MeasuredPitfallService> selfProvider;

    public MeasuredPitfallService(ObjectProvider<MeasuredPitfallService> selfProvider) {
        this.selfProvider = selfProvider;
    }

    public String callMeasuredMethodThroughThis(String input) {
        return measuredOperation(input);
    }

    public String callMeasuredMethodThroughProxy(String input) {
        return selfProvider.getObject().measuredOperation(input);
    }

    @Measured("spring-pitfall-measured-operation")
    public String measuredOperation(String input) {
        return input.toUpperCase();
    }
}
