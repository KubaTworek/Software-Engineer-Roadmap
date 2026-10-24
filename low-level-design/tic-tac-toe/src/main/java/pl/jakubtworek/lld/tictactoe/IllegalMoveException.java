package pl.jakubtworek.lld.tictactoe;

/**
 * Odmowa wykonania polecenia domenowego. Kod błędu jest częścią kontraktu:
 * klient i test mogą rozróżnić przyczynę bez parsowania polskiego komunikatu.
 * Nie łapiemy tego wyjątku w modelu, aby udawać poprawnie wykonany ruch.
 * Cel edukacyjny: oddzielić oczekiwaną odmowę reguły od awarii infrastruktury
 * i błędu użycia API. Zajęte pole to OCCUPIED, argument null daje w play
 * NullPointerException, a błąd odczytu konsoli jest IOException.
 * W tym ćwiczeniu odmowę sygnalizujemy wyjątkiem. Jawny typ wyniku byłby
 * alternatywą dla API, w którym odrzucenia są zwykłą, bardzo częstą odpowiedzią.
 */
public final class IllegalMoveException extends IllegalArgumentException {

    public enum Reason {

        // Kolejność elementów nie steruje walidacją. Priorytet odmów ustala
        // kod play i opisuje README; klient rozpoznaje nazwę, nie ordinal().
        GAME_FINISHED,
        WRONG_TURN,
        OUT_OF_BOUNDS,
        OCCUPIED
    }

    private final Reason reason;

    public IllegalMoveException(Reason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
