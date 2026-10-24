package pl.jakubtworek.lld.carrental;

/**
 * Kod klienta nie jest jego nazwiskiem ani dowodem uwierzytelnienia.
 */
public record CustomerId(String value) {

    public CustomerId {
        value = Text.id(value);
    }
}
