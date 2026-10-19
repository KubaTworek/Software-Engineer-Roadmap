package pl.jakubtworek.backend_engineering.stage_3.block_b.pipeline;

import java.util.Map;

/** A dependency boundary used by the executable telemetry pipeline. */
@FunctionalInterface
public interface PaymentDependency {

    void charge(String orderId, Map<String, String> outboundHeaders);
}
