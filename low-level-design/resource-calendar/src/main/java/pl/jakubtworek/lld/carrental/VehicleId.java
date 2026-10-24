package pl.jakubtworek.lld.carrental;

/**
 * Tożsamość fizycznego auta, niezależna od jego modelu i klasy cenowej.
 */
public record VehicleId(String value) {

    public VehicleId {
        value = Text.id(value);
    }
}
