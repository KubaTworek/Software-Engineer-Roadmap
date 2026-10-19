package pl.jakubtworek.backend_engineering.stage_2.block_a.grpc;

public record ProtoField(int number, String name, WireType type) {
    public ProtoField {
        if (number < 1 || name == null || name.isBlank()) {
            throw new IllegalArgumentException("positive number and name are required");
        }
    }

    public enum WireType {
        VARINT,
        FIXED_32,
        FIXED_64,
        LENGTH_DELIMITED
    }
}
