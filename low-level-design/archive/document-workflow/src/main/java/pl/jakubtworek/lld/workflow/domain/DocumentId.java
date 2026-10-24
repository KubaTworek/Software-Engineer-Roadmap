package pl.jakubtworek.lld.workflow.domain;

/**
 * Klucz dokumentu w tokenach. Nie jest kluczem operacji ani globalnym generatorem ID.
 */
public record DocumentId(String value) {

    public DocumentId {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Niepoprawne ID dokumentu");
    }

    @Override
    public String toString() {
        return value;
    }
}
