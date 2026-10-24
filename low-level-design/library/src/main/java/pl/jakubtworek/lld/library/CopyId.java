package pl.jakubtworek.lld.library;

/**
 * Kod konkretnego egzemplarza; dwa egzemplarze jednego tytułu mają różne ID.
 */
public record CopyId(String value) {

    public CopyId {
        value = Text.id(value);
    }
}
