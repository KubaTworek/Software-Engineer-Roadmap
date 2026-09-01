package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.idempotency;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.integers;
import static org.quicktheories.generators.SourceDSL.lists;

class IdempotencyProtocolStatefulPropertyTest {

    @Test
    void everyGeneratedCommandSequenceShouldBehaveLikeTheReferenceStateMachine() {
        qt().withExamples(500)
                .forAll(lists().of(integers().between(0, 5)).ofSizeBetween(1, 80))
                .checkAssert(this::assertSequence);
    }

    private void assertSequence(List<Integer> operationCodes) {
        IdempotencyProtocol protocol = new IdempotencyProtocol();
        ReferenceModel model = new ReferenceModel();

        for (int operationCode : operationCodes) {
            Command command = Command.from(operationCode);
            assertThat(execute(protocol, command))
                    .as("outcome after command %s in sequence %s", command, operationCodes)
                    .isEqualTo(model.execute(command));
            assertThat(protocol.snapshot("operation-1"))
                    .as("state after command %s in sequence %s", command, operationCodes)
                    .isEqualTo(model.snapshot());
        }
    }

    private static String execute(IdempotencyProtocol protocol, Command command) {
        try {
            return switch (command.action()) {
                case BEGIN -> "BEGIN:" + protocol.begin("operation-1", command.fingerprint()).status();
                case COMPLETE -> "COMPLETE:" + protocol.complete(
                        "operation-1", command.fingerprint(), command.result());
            };
        } catch (IdempotencyProtocol.IdempotencyConflictException exception) {
            return "ERROR:CONFLICT";
        } catch (IllegalStateException exception) {
            return "ERROR:NOT_STARTED";
        }
    }

    private enum Action {
        BEGIN,
        COMPLETE
    }

    private record Command(Action action, String fingerprint, String result) {

        static Command from(int code) {
            return switch (code) {
                case 0 -> new Command(Action.BEGIN, "request-a", "result-a");
                case 1 -> new Command(Action.BEGIN, "request-b", "result-b");
                case 2 -> new Command(Action.COMPLETE, "request-a", "result-a");
                case 3 -> new Command(Action.COMPLETE, "request-b", "result-b");
                case 4 -> new Command(Action.COMPLETE, "request-a", "changed-result");
                case 5 -> new Command(Action.BEGIN, "request-a", "result-a");
                default -> throw new IllegalArgumentException("unknown operation code");
            };
        }
    }

    /** Intentionally simple oracle, independent from the production implementation. */
    private static final class ReferenceModel {

        private String fingerprint;
        private String result;

        String execute(Command command) {
            if (command.action() == Action.BEGIN) {
                if (fingerprint == null) {
                    fingerprint = command.fingerprint();
                    return "BEGIN:STARTED";
                }
                if (!fingerprint.equals(command.fingerprint())) {
                    return "BEGIN:CONFLICT";
                }
                return result == null ? "BEGIN:IN_PROGRESS" : "BEGIN:REPLAY";
            }

            if (fingerprint == null) {
                return "ERROR:NOT_STARTED";
            }
            if (!fingerprint.equals(command.fingerprint())) {
                return "ERROR:CONFLICT";
            }
            if (result != null) {
                return result.equals(command.result())
                        ? "COMPLETE:ALREADY_COMPLETED"
                        : "ERROR:CONFLICT";
            }
            result = command.result();
            return "COMPLETE:COMPLETED";
        }

        Optional<IdempotencyProtocol.ProtocolSnapshot> snapshot() {
            if (fingerprint == null) {
                return Optional.empty();
            }
            return Optional.of(new IdempotencyProtocol.ProtocolSnapshot(
                    fingerprint,
                    result == null
                            ? IdempotencyProtocol.ProtocolState.PROCESSING
                            : IdempotencyProtocol.ProtocolState.COMPLETED,
                    Optional.ofNullable(result)
            ));
        }
    }
}
