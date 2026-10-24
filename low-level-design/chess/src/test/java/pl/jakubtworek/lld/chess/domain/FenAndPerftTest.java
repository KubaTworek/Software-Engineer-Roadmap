package pl.jakubtworek.lld.chess.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * FEN jest narzędziem powtarzalnego ustawiania danych. Oczekiwane wartości perft
 * pochodzą z tabel CPW, nie z zapisu wyniku badanego generatora. Pozycje te są
 * również używane przez testy Stockfish; źródła i granice opisuje README.
 */
class FenAndPerftTest {

    private final FenCodec fen = new FenCodec();
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String KIWIPETE = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    private static final String ENDGAME = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private static final String PROMOTIONS = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";

    @Test
    void standardPositionHasCanonicalFen() {
        assertEquals(START, fen.format(Position.initial()));
    }

    @ParameterizedTest
    @ValueSource(strings = { START, KIWIPETE, ENDGAME, PROMOTIONS,
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1" })
    void roundTripPreservesAllSixFields(String text) {
        assertEquals(text, fen.format(fen.parse(text)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "8/8/8/8/8/8/8/8 w - - 0 1", "4k3/8/8/8/8/8/8/4K3 w - - 0",
            "4k3/8/8/8/8/8/8/4K3 w - - 0 1 extra", "4k3/8/8/8/8/8/8/4K3 x - - 0 1", "4k3/8/8/8/8/8/8/4K3 w - - -1 1",
            "4k3/8/8/8/8/8/8/4K3 w - - 0 0", "4k3/8/8/8/8/8/8/4K3 w - - +1 1",
            "4k3/8/8/8/8/8/8/4K3 w - - 0 999999999999999999999999999", "4k3/8/8/8/8/8/8/4K3 w K - 0 1",
            "4k3/8/8/8/8/8/8/4K3 w KK - 0 1", "4k3/8/8/8/8/8/8/4K3 w qK - 0 1", "4k3/8/8/8/8/8/8/4K3 w X - 0 1",
            "4k3/8/8/8/8/8/8/4K3 w - e6 0 1", "4k3/8/8/8/4p3/8/8/4K3 w - e3 0 1", "4k3/8/8/4p3/8/8/8/4K3 w - e6 1 1",
            "4k3/4p3/8/4p3/8/8/8/4K3 w - e6 0 1", "4k3/8/4N3/4p3/8/8/8/4K3 w - e6 0 1",
            "4k3/8/8/8/8/8/4k3/4K3 w - - 0 1", "4k3/8/8/8/8/8/8/P3K3 w - - 0 1", "4k3/8/8/8/8/8/8/4KK2 w - - 0 1",
            "4k3/8/8/8/8/8/4R3/4K3 w - - 0 1", "8/8/8/8/8/8/4k3/4K3 w - - 0 1", "4k3/8/8/8/8/8/8/4K4 w - - 0 1",
            "4k3/8/8/8/8/8/8/4K2 w - - 0 1", "4k3/8/8/8/8/8/8/4Kx2 w - - 0 1", "4k3/8/8/8/8/8/8/4K12 w - - 0 1",
            "4k3/8/8/8/8/8/8/4K3 w - - 0 1" })
    void malformedOrLocallyInconsistentFenIsRejected(String text) {
        assertThrows(IllegalArgumentException.class, () -> fen.parse(text));
    }

    @Test
    void countersFollowQuietMovesPawnMovesAndCaptures() {
        var game = new ChessGame();
        game.play(Move.of("g1", "f3"));
        assertEquals(1, game.snapshot().position().halfmoveClock());
        assertEquals(1, game.snapshot().position().fullmoveNumber());
        game.play(Move.of("g8", "f6"));
        assertEquals(2, game.snapshot().position().halfmoveClock());
        assertEquals(2, game.snapshot().position().fullmoveNumber());
        game.play(Move.of("e2", "e4"));
        assertEquals(0, game.snapshot().position().halfmoveClock());
        game.play(Move.of("f6", "e4"));
        assertEquals(0, game.snapshot().position().halfmoveClock());
        assertEquals(3, game.snapshot().position().fullmoveNumber());
        assertEquals(fen.format(game.snapshot().position()), fen.format(fen.parse(fen.format(game.snapshot()
                .position()))));
    }

    @Test
    void fullmoveNumberRoundTripsAfterCrossingIntegerRange() {
        var rules = new ChessRules();
        Position start = fen.parse("7k/8/8/8/8/8/8/KR6 b - - 0 2147483647");
        Position next = rules.after(start, Move.of("h8", "h7"));
        assertEquals(2147483648L, next.fullmoveNumber());
        assertEquals(fen.format(next), fen.format(fen.parse(fen.format(next))));
    }

    @ParameterizedTest
    @CsvSource({ "1,48", "2,2039", "3,97862" })
    void kiwipeteIncludesCastlingAndEnPassantBranches(int depth, long expected) {
        assertEquals(expected, new Perft().count(fen.parse(KIWIPETE), depth));
    }

    @ParameterizedTest
    @CsvSource({ "1,14", "2,191", "3,2812", "4,43238" })
    void endgameExposesPawnAndRookInteractions(int depth, long expected) {
        assertEquals(expected, new Perft().count(fen.parse(ENDGAME), depth));
    }

    @ParameterizedTest
    @CsvSource({ "1,6", "2,264", "3,9467" })
    void promotionReferenceIncludesChecksAndUnderpromotions(int depth, long expected) {
        assertEquals(expected, new Perft().count(fen.parse(PROMOTIONS), depth));
    }

    @Test
    void divideMatchesTotalAndHandCountedOpeningBranches() {
        var branches = new Perft().divide(Position.initial(), 2);
        assertEquals(20, branches.size());
        assertTrue(branches.values().stream().allMatch(n -> n == 20));
        assertEquals(400, branches.values().stream().mapToLong(Long::longValue).sum());
        assertEquals(new Perft().count(fen.parse(KIWIPETE), 2), new Perft().divide(fen.parse(KIWIPETE), 2).values()
                .stream().mapToLong(Long::longValue).sum());
        assertThrows(UnsupportedOperationException.class, branches::clear);
        assertEquals(1, new Perft().count(Position.initial(), 0));
        assertThrows(IllegalArgumentException.class, () -> new Perft().divide(Position.initial(), 0));
        assertThrows(IllegalArgumentException.class, () -> new Perft().count(Position.initial(), -1));
    }

    @Test
    void optimizedGeneratorMatchesExhaustiveIntentOracleOnReferencePositions() {
        var rules = new ChessRules();
        // Niezależna enumeracja źródło × 64 cele × wybór promocji chroni przed
        // pominięciem ruchu przez nowy generator. Walidator jest wspólny, więc
        // ta wyrocznia bada kompletność enumeracji, nie niezależnie reguły szachów.
        for (String text : List.of(START, KIWIPETE, ENDGAME, PROMOTIONS)) {
            Position position = fen.parse(text);
            var exhaustive = new HashSet<Move>();
            for (var entry : position.board().pieces().entrySet()) {
                if (entry.getValue().color() != position.turn())
                    continue;
                for (int rank = 0; rank < 8; rank++) for (int file = 0; file < 8; file++) {
                    Square to = new Square(file, rank);
                    for (PieceType choice : PieceType.values()) {
                        if (!choice.canPromoteTo())
                            continue;
                        Move promotion = new Move(entry.getKey(), to, choice);
                        if (rules.evaluate(position, promotion) instanceof MoveEvaluation.Accepted)
                            exhaustive.add(promotion);
                    }
                    Move ordinary = new Move(entry.getKey(), to);
                    if (rules.evaluate(position, ordinary) instanceof MoveEvaluation.Accepted)
                        exhaustive.add(ordinary);
                }
            }
            assertEquals(exhaustive, new HashSet<>(rules.legalMoves(position)), text);
            assertEquals(!exhaustive.isEmpty(), rules.hasLegalMove(position));
        }
    }
}
