package pl.jakubtworek.lld.chess.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Jeden spójny odczyt: pozycja, status, wynik, uprawnienia do zgłoszeń i ruchy
 * z tej samej chwili. repetitions dotyczy bieżącego klucza w znanej historii.
 * Trwała lista ruchów i niemutowalne elementy zachowują dawny widok bez pełnej kopii.
 */
public record GameSnapshot(Position position, GameStatus status, List<Move> history, GameResult result,
        Set<DrawClaim> availableDrawClaims, int repetitions) {

    public GameSnapshot {
        Objects.requireNonNull(position);
        Objects.requireNonNull(status);
        history = MoveHistory.copyOf(history);
        Objects.requireNonNull(result);
        availableDrawClaims = Set.copyOf(availableDrawClaims);
        if (repetitions < 1)
            throw new IllegalArgumentException("Pozycja musi wystąpić co najmniej raz");
        if (result.finished() && !availableDrawClaims.isEmpty()) {
            throw new IllegalArgumentException("Zakończona gra nie udostępnia zgłoszeń");
        }
    }
}
