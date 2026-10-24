package pl.jakubtworek.lld.library;

/**
 * Kod opisu tytułu. Osobny typ zapobiega podaniu kodu egzemplarza jako tytułu.
 */
public record BookId(String value) {

    public BookId {
        value = Text.id(value);
    }
}
