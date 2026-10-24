package pl.jakubtworek.lld.chess.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.chess.domain.Color.*;
import static pl.jakubtworek.lld.chess.domain.GameResult.Termination.*;

/**
 * Testy badają historię i cykl życia, a nie samą geometrię. Każdy zapis FEN
 * rozpoczyna nową historię; nie udajemy, że półruchy z licznika znamy jako ruchy.
 */
class GameLifecycleTest {

    private final FenCodec fen = new FenCodec();

    @Test
    void thirdOccurrenceGivesClaimButDoesNotEndGame() {
        var game = new ChessGame();
        cycle(game);
        cycle(game);
        var before = game.snapshot();
        assertEquals(3, before.repetitions());
        assertFalse(before.result().finished());
        assertEquals(Set.of(DrawClaim.THREEFOLD_REPETITION), before.availableDrawClaims());
        assertFalse(game.legalMoves().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> before.availableDrawClaims().clear());
        commandRejected(game, () -> game.claimDraw(BLACK, DrawClaim.THREEFOLD_REPETITION),
                GameCommandException.Reason.WRONG_PLAYER);
        var claimed = game.claimDraw(WHITE, DrawClaim.THREEFOLD_REPETITION);
        assertEquals(THREEFOLD_REPETITION, claimed.result().termination());
        assertTrue(claimed.result().isDraw());
        assertEquals(before.history(), claimed.history());
        assertSame(before.position(), claimed.position());
        assertTrue(game.legalMoves().isEmpty());
        assertTrue(claimed.availableDrawClaims().isEmpty());
        assertSame(before, game.undo());
    }

    @Test
    void intendedMoveMayCreateThirdOccurrenceWithoutBeingPlayed() {
        var game = new ChessGame();
        cycle(game);
        play(game, "g1 f3", "g8 f6", "f3 g1");
        var before = game.snapshot();
        assertEquals(BLACK, before.position().turn());
        assertTrue(before.availableDrawClaims().isEmpty());
        game.claimDraw(BLACK, DrawClaim.THREEFOLD_REPETITION, Move.of("f6", "g8"));
        assertEquals(THREEFOLD_REPETITION, game.snapshot().result().termination());
        assertSame(before.position(), game.snapshot().position());
        assertEquals(7, game.snapshot().history().size());
        assertSame(before, game.undo());
    }

    @Test
    void failedClaimsAndIllegalIntendedMovesDoNotCreateHistory() {
        var game = new ChessGame();
        commandRejected(game, () -> game.claimDraw(WHITE, DrawClaim.THREEFOLD_REPETITION),
                GameCommandException.Reason.DRAW_NOT_AVAILABLE);
        commandRejected(game, () -> game.claimDraw(WHITE, DrawClaim.FIFTY_MOVE_RULE, Move.of("e2", "e4")),
                GameCommandException.Reason.DRAW_NOT_AVAILABLE);
        var before = game.snapshot();
        assertThrows(IllegalMoveException.class, () -> game.claimDraw(WHITE, DrawClaim.FIFTY_MOVE_RULE, Move.of("e2",
                "e5")));
        assertSame(before, game.snapshot());
        commandRejected(game, game::undo, GameCommandException.Reason.NO_HISTORY);
    }

    @Test
    void fifthOccurrenceIsAutomaticAndUndoRestoresCounts() {
        var game = new ChessGame();
        for (int i = 0; i < 4; i++) cycle(game);
        assertEquals(5, game.snapshot().repetitions());
        assertEquals(FIVEFOLD_REPETITION, game.snapshot().result().termination());
        assertEquals(16, game.snapshot().history().size());
        game.undo();
        assertFalse(game.snapshot().result().finished());
        assertEquals(4, game.snapshot().repetitions());
        game.play(Move.of("f6", "g8"));
        assertEquals(5, game.snapshot().repetitions());
        assertEquals(FIVEFOLD_REPETITION, game.snapshot().result().termination());
    }

    @Test
    void abandonedBranchDoesNotInflateFutureRepetitions() {
        var game = new ChessGame();
        var start = game.snapshot();
        cycle(game);
        cycle(game);
        for (int i = 0; i < 8; i++) game.undo();
        assertSame(start, game.snapshot());
        cycle(game);
        assertEquals(2, game.snapshot().repetitions());
        assertTrue(game.snapshot().availableDrawClaims().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({ "99,false,false", "100,true,false", "149,true,false", "150,false,true" })
    void halfmoveThresholdsDistinguishClaimFromAutomaticDraw(long clock, boolean claim, boolean finished) {
        var game = quietGame(clock);
        assertEquals(claim, game.snapshot().availableDrawClaims().contains(DrawClaim.FIFTY_MOVE_RULE));
        assertEquals(finished, game.snapshot().result().finished());
        assertEquals(1, game.snapshot().repetitions());
    }

    @Test
    void fiftyMoveClaimHasCurrentAndIntendedVariants() {
        var current = quietGame(100);
        current.claimDraw(WHITE, DrawClaim.FIFTY_MOVE_RULE);
        assertEquals(FIFTY_MOVE_RULE, current.snapshot().result().termination());
        var intended = quietGame(99);
        var before = intended.snapshot();
        intended.claimDraw(WHITE, DrawClaim.FIFTY_MOVE_RULE, Move.of("b1", "b2"));
        assertEquals(FIFTY_MOVE_RULE, intended.snapshot().result().termination());
        assertEquals(99, intended.snapshot().position().halfmoveClock());
        assertSame(before.position(), intended.snapshot().position());
        assertTrue(intended.snapshot().history().isEmpty());
        assertSame(before, intended.undo());
    }

    @Test
    void pawnMoveAndCaptureResetClockAndCannotSupportIntendedFiftyClaim() {
        var pawn = new ChessGame(fen.parse("7k/8/8/8/8/8/P7/KR6 w - - 99 50"));
        commandRejected(pawn, () -> pawn.claimDraw(WHITE, DrawClaim.FIFTY_MOVE_RULE, Move.of("a2", "a3")),
                GameCommandException.Reason.DRAW_NOT_AVAILABLE);
        pawn.play(Move.of("a2", "a3"));
        assertEquals(0, pawn.snapshot().position().halfmoveClock());
        var capture = new ChessGame(fen.parse("7k/8/8/8/8/8/1n6/KR6 w - - 99 50"));
        commandRejected(capture, () -> capture.claimDraw(WHITE, DrawClaim.FIFTY_MOVE_RULE, Move.of("b1", "b2")),
                GameCommandException.Reason.DRAW_NOT_AVAILABLE);
        capture.play(Move.of("b1", "b2"));
        assertEquals(0, capture.snapshot().position().halfmoveClock());
    }

    @Test
    void reaching150HalfmovesAutomaticallyDrawsButMateTakesPrecedence() {
        var quiet = quietGame(149);
        quiet.play(Move.of("b1", "b2"));
        assertEquals(SEVENTY_FIVE_MOVE_RULE, quiet.snapshot().result().termination());
        assertTrue(quiet.legalMoves().isEmpty());
        var mating = new ChessGame(fen.parse("7k/8/5KQ1/8/8/8/8/8 w - - 149 80"));
        mating.play(Move.of("g6", "g7"));
        assertEquals(150, mating.snapshot().position().halfmoveClock());
        assertEquals(CHECKMATE, mating.snapshot().result().termination());
        assertEquals(Optional.of(WHITE), mating.snapshot().result().winner());
    }

    @Test
    void mateAndStalemateHaveExplicitResults() {
        var mate = new ChessGame();
        play(mate, "f2 f3", "e7 e5", "g2 g4", "d8 h4");
        assertEquals(CHECKMATE, mate.snapshot().result().termination());
        assertEquals(Optional.of(BLACK), mate.snapshot().result().winner());
        var stalemate = new ChessGame(fen.parse("k7/8/1QK5/8/8/8/8/8 b - - 0 1"));
        assertEquals(STALEMATE, stalemate.snapshot().result().termination());
        assertTrue(stalemate.snapshot().result().winner().isEmpty());
    }

    @Test
    void resignationIsIndependentOfTurnAndUndoReopensSamePosition() {
        var game = new ChessGame();
        var before = game.snapshot();
        game.resign(BLACK);
        assertEquals(GameStatus.ONGOING, game.snapshot().status());
        assertEquals(RESIGNATION, game.snapshot().result().termination());
        assertEquals(Optional.of(WHITE), game.snapshot().result().winner());
        assertSame(before.position(), game.snapshot().position());
        assertTrue(game.snapshot().history().isEmpty());
        assertTrue(game.legalMoves().isEmpty());
        commandRejected(game, () -> game.resign(WHITE), GameCommandException.Reason.GAME_OVER);
        commandRejected(game, () -> game.claimDraw(WHITE, DrawClaim.FIFTY_MOVE_RULE),
                GameCommandException.Reason.GAME_OVER);
        assertThrows(IllegalMoveException.class, () -> game.play(Move.of("e2", "e4")));
        assertSame(before, game.undo());
        game.play(Move.of("e2", "e4"));
        assertFalse(game.snapshot().result().finished());
    }

    @ParameterizedTest
    @ValueSource(strings = { "e1 g1", "e1 c1" })
    void undoCastlingRestoresBothPiecesRightsAndCounters(String text) {
        var game = new ChessGame(fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 12 20"));
        var before = game.snapshot();
        String[] fields = text.split(" ");
        game.play(Move.of(fields[0], fields[1]));
        assertFalse(game.snapshot().position().rights().whiteKingSide());
        assertSame(before, game.undo());
        assertEquals("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 12 20", fen.format(game.snapshot().position()));
        game.play(Move.of(fields[0], fields[1]));
    }

    @Test
    void undoEnPassantAndDoubleStepRestoresWindowVictimTurnAndFullmove() {
        var game = new ChessGame(fen.parse("4k3/3p4/8/4P3/8/8/8/4K3 b - - 7 20"));
        var start = game.snapshot();
        game.play(Move.of("d7", "d5"));
        var window = game.snapshot();
        assertEquals(21, window.position().fullmoveNumber());
        game.play(Move.of("e5", "d6"));
        assertTrue(game.snapshot().position().board().at(Square.parse("d5")).isEmpty());
        assertSame(window, game.undo());
        assertEquals(Square.parse("d6"), game.snapshot().position().enPassantTarget().orElseThrow());
        assertSame(start, game.undo());
        assertEquals(7, game.snapshot().position().halfmoveClock());
        assertEquals(20, game.snapshot().position().fullmoveNumber());
    }

    @Test
    void undoPromotionRestoresPawnAndCapturedPiece() {
        var game = new ChessGame(fen.parse("5r2/4P2k/8/8/8/8/8/K7 w - - 0 20"));
        var before = game.snapshot();
        game.play(Move.promote("e7", "f8", PieceType.KNIGHT));
        assertSame(before, game.undo());
        assertEquals(new Piece(WHITE, PieceType.PAWN), game.snapshot().position().board().at(Square.parse("e7"))
                .orElseThrow());
        assertEquals(new Piece(BLACK, PieceType.ROOK), game.snapshot().position().board().at(Square.parse("f8"))
                .orElseThrow());
    }

    @Test
    void positionIdentityIgnoresCountersButIncludesTurnAndRights() {
        String board = "r3k2r/8/8/8/8/8/8/R3K2R";
        var base = PositionKey.of(fen.parse(board + " w KQkq - 0 1"));
        assertEquals(base, PositionKey.of(fen.parse(board + " w KQkq - 80 99")));
        assertNotEquals(base, PositionKey.of(fen.parse(board + " b KQkq - 0 1")));
        assertNotEquals(base, PositionKey.of(fen.parse(board + " w Qkq - 0 1")));
    }

    @Test
    void repetitionKeyIncludesOnlyLegallyUsableEnPassant() {
        String available = "7k/8/8/5pP1/8/8/8/K7 w - ";
        assertNotEquals(key(available + "f6 0 2"), key(available + "- 0 2"));
        String unavailable = "7k/8/8/5p2/8/8/8/K7 w - ";
        assertEquals(key(unavailable + "f6 0 2"), key(unavailable + "- 0 2"));
        // Oba piony znikają z piątej linii, odsłaniając wieżę a5 do króla h5.
        String pinned = "7k/8/8/r4pPK/8/8/8/8 w - ";
        assertEquals(key(pinned + "f6 0 2"), key(pinned + "- 0 2"));
    }

    @Test
    void fenReloadKeepsCountersButStartsIndependentRepetitionHistory() {
        var game = new ChessGame();
        cycle(game);
        cycle(game);
        var imported = new ChessGame(fen.parse(fen.format(game.snapshot().position())));
        assertEquals(1, imported.snapshot().repetitions());
        assertEquals(8, imported.snapshot().position().halfmoveClock());
        assertTrue(imported.snapshot().history().isEmpty());
        assertTrue(imported.snapshot().availableDrawClaims().isEmpty());
    }

    @Test
    void resultCannotContradictItsTermination() {
        assertThrows(IllegalArgumentException.class, () -> new GameResult(CHECKMATE, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new GameResult(FIFTY_MOVE_RULE, Optional.of(WHITE)));
        assertThrows(IllegalArgumentException.class, () -> GameResult.draw(IN_PROGRESS));
        assertThrows(IllegalArgumentException.class, () -> GameResult.win(IN_PROGRESS, WHITE));
    }

    @Test
    void perftIgnoresHistoricalGameTermination() {
        var game = quietGame(150);
        assertTrue(game.legalMoves().isEmpty());
        assertTrue(new Perft().count(game.snapshot().position(), 1) > 0);
    }

    private PositionKey key(String text) {
        return PositionKey.of(fen.parse(text));
    }

    private ChessGame quietGame(long clock) {
        return new ChessGame(fen.parse("7k/8/8/8/8/8/8/KR6 w - - " + clock + " 80"));
    }

    /**
     * Cykl skoczków nie zmienia praw roszady ani układu pionów: wraca ta sama pozycja.
     */
    private static void cycle(ChessGame game) {
        play(game, "g1 f3", "g8 f6", "f3 g1", "f6 g8");
    }

    private static void play(ChessGame game, String... moves) {
        for (String text : moves) {
            String[] fields = text.split(" ");
            game.play(Move.of(fields[0], fields[1]));
        }
    }

    /**
     * Odmowa jest transakcyjna również dla komend innych niż ruch.
     */
    private static void commandRejected(ChessGame game, Runnable command, GameCommandException.Reason reason) {
        var before = game.snapshot();
        assertEquals(reason, assertThrows(GameCommandException.class, command::run).reason());
        assertSame(before, game.snapshot());
    }
}
