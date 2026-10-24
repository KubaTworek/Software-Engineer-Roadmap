package pl.jakubtworek.lld.library;

/**
 * Tożsamość czytelnika nie zależy od nazwiska, które nie musi być unikalne.
 */
public record MemberId(String value) {

    public MemberId {
        value = Text.id(value);
    }
}
