package pl.jakubtworek.lld.workflow.domain;

/**
 * Tożsamość osoby, niezależna od jej ról. Dwie role nie tworzą dwóch osób.
 */
public record UserId(String value) {

    public UserId {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Niepoprawne ID użytkownika");
    }

    @Override
    public String toString() {
        return value;
    }
}
