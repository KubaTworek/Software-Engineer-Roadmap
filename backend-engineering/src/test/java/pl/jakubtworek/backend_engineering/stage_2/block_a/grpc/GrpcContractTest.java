package pl.jakubtworek.backend_engineering.stage_2.block_a.grpc;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class GrpcContractTest {

    @Test
    void removedFieldNumberMustBeReserved() {
        ProtoSchema v1 = new ProtoSchema(List.of(
                new ProtoField(1, "order_id", ProtoField.WireType.LENGTH_DELIMITED),
                new ProtoField(2, "amount", ProtoField.WireType.VARINT)), Set.of());
        ProtoSchema unsafeV2 = new ProtoSchema(List.of(
                new ProtoField(1, "order_id", ProtoField.WireType.LENGTH_DELIMITED)), Set.of());
        ProtoSchema safeV2 = new ProtoSchema(List.of(
                new ProtoField(1, "order_id", ProtoField.WireType.LENGTH_DELIMITED)), Set.of(2));

        ProtoCompatibilityChecker checker = new ProtoCompatibilityChecker();
        assertThat(checker.safeEvolutionViolations(v1, unsafeV2))
                .containsExactly("removed field number 2 must be reserved");
        assertThat(checker.safeEvolutionViolations(v1, safeV2)).isEmpty();
    }

    @Test
    void fieldNumberCannotBeReusedForDifferentMeaning() {
        ProtoSchema v1 = new ProtoSchema(List.of(
                new ProtoField(1, "order_id", ProtoField.WireType.LENGTH_DELIMITED)), Set.of());
        ProtoSchema v2 = new ProtoSchema(List.of(
                new ProtoField(1, "customer_id", ProtoField.WireType.LENGTH_DELIMITED)), Set.of());

        assertThat(new ProtoCompatibilityChecker().safeEvolutionViolations(v1, v2))
                .containsExactly("field number 1 was reused by customer_id");
    }

    @Test
    void childDeadlineCannotOutliveParent() {
        AtomicLong time = new AtomicLong(1_000);
        RpcDeadline parent = RpcDeadline.after(Duration.ofMillis(100), time::get);

        time.addAndGet(Duration.ofMillis(60).toNanos());
        RpcDeadline child = parent.child(Duration.ofMillis(80));

        assertThat(child.remaining()).isEqualTo(Duration.ofMillis(40));
    }

    @Test
    void retryRequiresIdempotencyRetryableStatusAndRemainingBudget() {
        AtomicLong time = new AtomicLong(0);
        RpcDeadline deadline = RpcDeadline.after(Duration.ofMillis(10), time::get);
        GrpcRetryPolicy policy = new GrpcRetryPolicy(3);

        assertThat(policy.shouldRetry(GrpcRetryPolicy.StatusCode.UNAVAILABLE, true, 1, deadline)).isTrue();
        assertThat(policy.shouldRetry(GrpcRetryPolicy.StatusCode.UNAVAILABLE, false, 1, deadline)).isFalse();
        assertThat(policy.shouldRetry(GrpcRetryPolicy.StatusCode.INVALID_ARGUMENT, true, 1, deadline)).isFalse();

        time.addAndGet(Duration.ofMillis(10).toNanos());
        assertThat(policy.shouldRetry(GrpcRetryPolicy.StatusCode.UNAVAILABLE, true, 1, deadline)).isFalse();
    }
}
