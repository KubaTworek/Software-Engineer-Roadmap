package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Demonstrates explicit ThreadLocal scoping. A regular ThreadLocal is not
 * automatically copied from a submitting thread to a new virtual thread.
 */
public final class RequestContextScope {

    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    public Optional<RequestContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public <T> T callWith(RequestContext context, Callable<T> action) throws Exception {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (CURRENT.get() != null) {
            throw new IllegalStateException("a request context is already active on this thread");
        }

        CURRENT.set(context);
        try {
            return action.call();
        } finally {
            CURRENT.remove();
        }
    }
}
