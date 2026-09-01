package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.correctness;

import java.util.Objects;

/** A completed operation interval used by the small linearizability checker. */
public record RegisterCall(
        String operationId,
        Type type,
        Integer argument,
        int result,
        long invokedAt,
        long completedAt) {

    public RegisterCall {
        Objects.requireNonNull(operationId, "operationId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (invokedAt < 0 || completedAt < invokedAt) {
            throw new IllegalArgumentException("operation interval is invalid");
        }
        if (type == Type.WRITE && argument == null) {
            throw new IllegalArgumentException("write requires an argument");
        }
        if (type == Type.READ && argument != null) {
            throw new IllegalArgumentException("read cannot have an argument");
        }
    }

    public static RegisterCall write(String id, int value, long invokedAt, long completedAt) {
        return new RegisterCall(id, Type.WRITE, value, value, invokedAt, completedAt);
    }

    public static RegisterCall read(String id, int observed, long invokedAt, long completedAt) {
        return new RegisterCall(id, Type.READ, null, observed, invokedAt, completedAt);
    }

    public enum Type {
        READ,
        WRITE
    }
}
