package pl.jakubtworek.lld.chess.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static pl.jakubtworek.lld.chess.domain.GameResult.Termination.*;

/**
 * Właściciel jednowątkowej gry. Każda zaakceptowana komenda publikuje jedną
 * wersję zawierającą snapshot, liczniki powtórzeń i odnośnik do poprzednika.
 * Cofanie odtwarza CAŁY stan, a nowa gałąź nie odziedziczy porzuconych powtórzeń.
 * Trwała lista ruchów i odwracalne delty indeksu powtórzeń usuwają koszt O(H²).
 * Odrzucona komenda nie zapisuje delty; nowa gałąź nie dziedziczy porzuconych wystąpień.
 */
public final class ChessGame {

    private final ChessRules rules = new ChessRules();
    private State state;

    // Mapa opisuje tylko bieżącą gałąź. Każdy State pamięta jedną deltę,
    // którą undo odwraca; cofnięcie komendy zakończenia nie zmienia liczników.
    private final Map<PositionKey, Integer> occurrences = new HashMap<>();

    private record State(GameSnapshot snapshot, PositionKey incremented, State previous) {
    }

    public ChessGame() {
        this(Position.initial());
    }

    /**
     * Import pozycji nie importuje przeszłości: pierwsze znane wystąpienie ma licznik 1.
     */
    public ChessGame(Position start) {
        Objects.requireNonNull(start);
        GameStatus status = rules.status(start);
        GameResult result = automaticResult(start, status, 1);
        occurrences.put(PositionKey.of(start), 1);
        state = new State(snapshot(start, status, List.of(), result, 1), null, null);
    }

    public GameSnapshot snapshot() {
        return state.snapshot();
    }

    public List<Move> legalMoves() {
        return snapshot().result().finished() ? List.of() : rules.legalMoves(snapshot().position());
    }

    public GameSnapshot play(Move move) {
        Objects.requireNonNull(move);
        if (snapshot().result().finished())
            throw new IllegalMoveException(IllegalMoveException.Reason.GAME_OVER);
        Position next = rules.after(snapshot().position(), move);
        PositionKey key = PositionKey.of(next);
        int repetitions = Math.addExact(occurrences.getOrDefault(key, 0), 1);
        GameStatus status = rules.status(next);
        GameResult result = automaticResult(next, status, repetitions);
        var history = MoveHistory.copyOf(snapshot().history()).append(move);
        State candidate = new State(snapshot(next, status, history, result, repetitions), key, state);
        occurrences.put(key, repetitions);
        // Wszystkie obliczenia mogące odmówić były przed aktualizacją indeksu.
        state = candidate;
        return snapshot();
    }

    /**
     * Rezygnacja jest komendą dowolnej strony, nie ruchem figury. W tym ćwiczeniu
     * oznacza przegraną rezygnującego; wyjątek dla niemożności mata i pełne
     * rozpoznawanie martwych pozycji są poza zakresem (opis w README).
     */
    public GameSnapshot resign(Color player) {
        Objects.requireNonNull(player);
        requireActive();
        return conclude(GameResult.win(RESIGNATION, player.opposite()));
    }

    public GameSnapshot claimDraw(Color player, DrawClaim claim) {
        Objects.requireNonNull(claim);
        requireClaimant(player);
        if (!snapshot().availableDrawClaims().contains(claim)) {
            throw new GameCommandException(GameCommandException.Reason.DRAW_NOT_AVAILABLE);
        }
        return conclude(claimResult(claim));
    }

    /**
     * Zgłoszenie z zamierzonym ruchem bada jego legalny rezultat, ale go NIE
     * wykonuje. Sukces kończy grę przy bieżącej planszy i nie dopisuje ruchu.
     * Odmowa też niczego nie zmienia; nie modelujemy kar turniejowych/zegara.
     */
    public GameSnapshot claimDraw(Color player, DrawClaim claim, Move intended) {
        Objects.requireNonNull(claim);
        Objects.requireNonNull(intended);
        requireClaimant(player);
        Position next = rules.after(snapshot().position(), intended);
        int repetitions = Math.addExact(occurrences.getOrDefault(PositionKey.of(next), 0), 1);
        if (!claims(next, repetitions).contains(claim)) {
            throw new GameCommandException(GameCommandException.Reason.DRAW_NOT_AVAILABLE);
        }
        return conclude(claimResult(claim));
    }

    /**
     * Cofa ostatnią zaakceptowaną komendę: ruch, rezygnację albo zgłoszenie.
     * Odtwarza oryginalny snapshot i mapę powtórzeń; brak historii jest odmową.
     * Cofanie jest narzędziem edukacyjnym/analizy, nie regułą partii turniejowej.
     */
    public GameSnapshot undo() {
        if (state.previous() == null)
            throw new GameCommandException(GameCommandException.Reason.NO_HISTORY);
        // Cofamy jedną deltę mapy, nie kopiujemy wszystkich liczników.
        // Zgłoszenie remisu/rezygnacja nie dodało pozycji: incremented jest null.
        if (state.incremented() != null) {
            PositionKey key = state.incremented();
            int count = occurrences.get(key);
            if (count == 1) {
                occurrences.remove(key);
            } else {
                occurrences.put(key, count - 1);
            }
        }
        state = state.previous();
        return snapshot();
    }

    private GameResult automaticResult(Position position, GameStatus status, int repetitions) {
        // Mat ma pierwszeństwo przed limitem 150 półruchów. Nie traktujemy
        // uprawnienia do zgłoszenia (3/100) jako automatycznego wyniku (5/150).
        if (status == GameStatus.CHECKMATE)
            return GameResult.win(CHECKMATE, position.turn().opposite());
        if (status == GameStatus.STALEMATE)
            return GameResult.draw(STALEMATE);
        if (repetitions >= 5)
            return GameResult.draw(FIVEFOLD_REPETITION);
        if (position.halfmoveClock() >= 150)
            return GameResult.draw(SEVENTY_FIVE_MOVE_RULE);
        return GameResult.ongoing();
    }

    private Set<DrawClaim> claims(Position position, int repetitions) {
        var result = new HashSet<DrawClaim>();
        if (repetitions >= 3)
            result.add(DrawClaim.THREEFOLD_REPETITION);
        if (position.halfmoveClock() >= 100)
            result.add(DrawClaim.FIFTY_MOVE_RULE);
        return Set.copyOf(result);
    }

    private GameSnapshot snapshot(Position position, GameStatus status, List<Move> history, GameResult result,
            int repetitions) {
        return new GameSnapshot(position, status, history, result, result.finished() ? Set.of() : claims(position,
                repetitions), repetitions);
    }

    private GameResult claimResult(DrawClaim claim) {
        return GameResult.draw(claim == DrawClaim.THREEFOLD_REPETITION ? THREEFOLD_REPETITION : FIFTY_MOVE_RULE);
    }

    private GameSnapshot conclude(GameResult result) {
        var old = snapshot();
        State candidate = new State(snapshot(old.position(), old.status(), old.history(), result, old.repetitions()),
                null, state);
        state = candidate;
        return snapshot();
    }

    private void requireActive() {
        if (snapshot().result().finished())
            throw new GameCommandException(GameCommandException.Reason.GAME_OVER);
    }

    private void requireClaimant(Color player) {
        Objects.requireNonNull(player);
        requireActive();
        if (player != snapshot().position().turn())
            throw new GameCommandException(GameCommandException.Reason.WRONG_PLAYER);
    }
}
