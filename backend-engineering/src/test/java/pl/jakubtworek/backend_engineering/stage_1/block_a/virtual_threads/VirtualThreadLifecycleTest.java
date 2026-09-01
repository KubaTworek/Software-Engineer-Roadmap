package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VirtualThreadLifecycleTest {

    @Test
    void cancellingFutureInterruptsBlockingVirtualThreadAndCodeRestoresTheFlag() throws Exception {
        CooperativeCancellation cancellation = new CooperativeCancellation();
        CountDownLatch operationStarted = new CountDownLatch(1);
        CountDownLatch neverReleasedNormally = new CountDownLatch(1);
        CountDownLatch interruptionHandled = new CountDownLatch(1);
        AtomicBoolean restoredFlag = new AtomicBoolean();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<CancellationState> future = executor.submit(() -> cancellation.run(() -> {
                operationStarted.countDown();
                neverReleasedNormally.await();
                return null;
            }, interrupted -> {
                restoredFlag.set(interrupted);
                interruptionHandled.countDown();
            }));

            assertThat(operationStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(future.cancel(true)).isTrue();
            assertThat(interruptionHandled.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(restoredFlag).isTrue();
            assertThat(future).isCancelled();
        }
    }

    @Test
    void regularThreadLocalRequiresExplicitContextInstallationInChildVirtualThread() throws Exception {
        RequestContextScope scope = new RequestContextScope();
        RequestContext context = new RequestContext("request-42", "alice");

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            scope.callWith(context, () -> {
                Future<RequestContext> implicit = executor.submit(() -> scope.current().orElse(null));
                Future<RequestContext> explicit = executor.submit(
                        () -> scope.callWith(context, () -> scope.current().orElseThrow())
                );

                assertThat(implicit.get()).isNull();
                assertThat(explicit.get()).isEqualTo(context);
                assertThat(scope.current()).contains(context);
                return null;
            });
        }

        assertThat(scope.current()).isEmpty();
    }

    @Test
    void requestContextIsRemovedEvenWhenTheRequestFails() {
        RequestContextScope scope = new RequestContextScope();
        RequestContext context = new RequestContext("request-42", "alice");

        assertThatThrownBy(() -> scope.callWith(context, () -> {
            throw new IllegalStateException("request failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(scope.current()).isEmpty();
    }

    @Test
    void blockingInsideSynchronizedHasADifferentPinningShapeThanBlockingOutside() throws Exception {
        PinningExamples examples = new PinningExamples();

        boolean monitorHeldInside = examples.blockingWhileHoldingMonitor(
                examples::currentThreadHoldsMonitor
        );
        boolean monitorHeldOutside = examples.updateStateThenBlockOutsideMonitor(
                examples::currentThreadHoldsMonitor
        );

        assertThat(monitorHeldInside).isTrue();
        assertThat(monitorHeldOutside).isFalse();
        assertThat(examples.state()).isEqualTo("completed");
    }
}
