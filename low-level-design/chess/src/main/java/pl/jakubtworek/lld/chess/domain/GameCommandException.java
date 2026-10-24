package pl.jakubtworek.lld.chess.domain;

/**
 * Błędy cyklu życia inne niż nielegalny ruch. Każdy pozostawia całą wersję gry bez zmian.
 */
public final class GameCommandException extends IllegalArgumentException {

    public enum Reason {

        GAME_OVER, WRONG_PLAYER, DRAW_NOT_AVAILABLE, NO_HISTORY
    }

    private final Reason reason;

    public GameCommandException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
