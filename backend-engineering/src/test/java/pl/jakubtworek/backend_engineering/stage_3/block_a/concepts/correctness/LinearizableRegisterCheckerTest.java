package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinearizableRegisterCheckerTest {

    private final LinearizableRegisterChecker checker = new LinearizableRegisterChecker();

    @Test
    void overlappingOperationsCanStillHaveALegalSequentialExplanation() {
        List<RegisterCall> history = List.of(
                RegisterCall.write("slow-write", 1, 0, 4),
                RegisterCall.write("fast-write", 2, 1, 2),
                RegisterCall.read("read", 1, 3, 5));

        assertThat(checker.isLinearizable(0, history)).isTrue();
    }

    @Test
    void aReadCannotReturnAnOldValueAfterACompletedWrite() {
        List<RegisterCall> history = List.of(
                RegisterCall.write("write", 7, 0, 1),
                RegisterCall.read("read", 0, 2, 3));

        assertThat(checker.isLinearizable(0, history)).isFalse();
    }

    @Test
    void theFinalValueAloneCannotDistinguishAValidFromAnInvalidHistory() {
        List<RegisterCall> validHistory = List.of(
                RegisterCall.write("write", 7, 0, 1),
                RegisterCall.read("read", 7, 2, 3));
        List<RegisterCall> invalidHistoryWithTheSameFinalWrite = List.of(
                RegisterCall.write("write", 7, 0, 1),
                RegisterCall.read("read", 0, 2, 3));

        assertThat(checker.isLinearizable(0, validHistory)).isTrue();
        assertThat(checker.isLinearizable(0, invalidHistoryWithTheSameFinalWrite)).isFalse();
    }
}
