package pl.jakubtworek.booking.aop;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MeasurementRegistry {
    private final CopyOnWriteArrayList<MeasuredCall> calls = new CopyOnWriteArrayList<>();

    public void record(MeasuredCall call) {
        calls.add(call);
    }

    public List<MeasuredCall> findAll() {
        return new ArrayList<>(calls);
    }

    public void clear() {
        calls.clear();
    }
}
