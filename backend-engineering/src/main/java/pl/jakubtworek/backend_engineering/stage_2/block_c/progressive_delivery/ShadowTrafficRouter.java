package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Mirrors sanitized input to a read-only shadow handler and always returns the primary response. */
public final class ShadowTrafficRouter {

    private final Executor shadowExecutor;

    public ShadowTrafficRouter(Executor shadowExecutor) {
        this.shadowExecutor = Objects.requireNonNull(shadowExecutor);
    }

    public record ProductionRequest(String correlationId, String payload, String authorization) {
        public ProductionRequest {
            Objects.requireNonNull(correlationId);
            Objects.requireNonNull(payload);
        }
    }

    public record ShadowRequest(String correlationId, String payload) {}

    public record Response(int status, String body) {}

    @FunctionalInterface
    public interface PrimaryHandler {
        Response handle(ProductionRequest request);
    }

    @FunctionalInterface
    public interface ReadOnlyShadowHandler {
        void evaluate(ShadowRequest request);
    }

    public Response route(ProductionRequest request,
                          PrimaryHandler primary,
                          ReadOnlyShadowHandler shadow,
                          Consumer<RuntimeException> shadowFailureObserver) {
        Response response = primary.handle(request);
        try {
            shadowExecutor.execute(() -> {
                try {
                    // Authorization is intentionally not copied and no write capability is exposed.
                    shadow.evaluate(new ShadowRequest(request.correlationId(), request.payload()));
                } catch (RuntimeException shadowFailure) {
                    shadowFailureObserver.accept(shadowFailure);
                }
            });
        } catch (RuntimeException schedulingFailure) {
            shadowFailureObserver.accept(schedulingFailure);
        }
        return response;
    }
}
