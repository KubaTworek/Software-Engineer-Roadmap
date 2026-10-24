package pl.jakubtworek.lld.chess.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Wynik jest niezależny od sytuacji króla: można zrezygnować w pozycji ONGOING
 * albo zgłosić remis podczas CHECK. Konstruktor nie pozwala przypisać zwycięzcy
 * remisowi ani pominąć zwycięzcy mata. Brak wyniku to jawne IN_PROGRESS.
 */
public record GameResult(Termination termination, Optional<Color> winner) {

    public enum Termination {

        IN_PROGRESS,
        CHECKMATE,
        STALEMATE,
        RESIGNATION,
        THREEFOLD_REPETITION,
        FIFTY_MOVE_RULE,
        FIVEFOLD_REPETITION,
        SEVENTY_FIVE_MOVE_RULE
    }

    public GameResult {
        Objects.requireNonNull(termination);
        Objects.requireNonNull(winner);
        boolean decisive = termination == Termination.CHECKMATE || termination == Termination.RESIGNATION;
        if (decisive != winner.isPresent())
            throw new IllegalArgumentException("Niespójny zwycięzca i przyczyna zakończenia");
    }

    public static GameResult ongoing() {
        return new GameResult(Termination.IN_PROGRESS, Optional.empty());
    }

    public static GameResult draw(Termination termination) {
        if (termination == Termination.IN_PROGRESS)
            throw new IllegalArgumentException("Remis musi mieć przyczynę");
        return new GameResult(termination, Optional.empty());
    }

    public static GameResult win(Termination termination, Color winner) {
        return new GameResult(termination, Optional.of(winner));
    }

    public boolean finished() {
        return termination != Termination.IN_PROGRESS;
    }

    public boolean isDraw() {
        return finished() && winner.isEmpty();
    }
}
