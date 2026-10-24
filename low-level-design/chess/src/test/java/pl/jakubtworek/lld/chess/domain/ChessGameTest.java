package pl.jakubtworek.lld.chess.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.chess.domain.Color.*;
import static pl.jakubtworek.lld.chess.domain.IllegalMoveException.Reason.*;
import static pl.jakubtworek.lld.chess.domain.PieceType.*;

/**
 * Przykłady są małymi kontrprzykładami dla błędnych modeli. Pozycje analityczne
 * skracają drogę do reguły, a pełne partie sprawdzają współpracę obiektów.
 * Nie jest to dowód poprawności wszystkich osiągalnych pozycji szachowych.
 */
class ChessGameTest {

    private final ChessRules rules = new ChessRules();

    @Test
    void openingMovesAndRejectedCommandPreserveSnapshot() {
        var game = new ChessGame();
        var start = game.snapshot();
        reject(game, Move.of("e2", "e5"), ILLEGAL_GEOMETRY);
        assertSame(start, game.snapshot());
        game.play(Move.of("e2", "e4"));
        assertEquals(BLACK, game.snapshot().position().turn());
        assertEquals(s("e3"), game.snapshot().position().enPassantTarget().orElseThrow());
        assertTrue(start.position().board().at(s("e2")).isPresent());
        assertTrue(start.history().isEmpty());
        assertEquals(List.of(Move.of("e2", "e4")), game.snapshot().history());
        reject(game, Move.of("d2", "d4"), WRONG_TURN);
    }

    @ParameterizedTest
    @CsvSource({ "e2,e2,SAME_SQUARE", "e3,e4,EMPTY_SOURCE", "e7,e5,WRONG_TURN", "e1,e2,OWN_PIECE", "a1,a3,BLOCKED",
            "c1,h6,BLOCKED", "d1,h5,BLOCKED", "b1,b3,ILLEGAL_GEOMETRY", "e2,d3,ILLEGAL_GEOMETRY", "e2,e1,OWN_PIECE" })
    void openingRefusals(String from, String to, IllegalMoveException.Reason reason) {
        reject(new ChessGame(), Move.of(from, to), reason);
    }

    @ParameterizedTest
    @CsvSource({ "ROOK,d4,d7", "ROOK,d4,g4", "BISHOP,d4,g7", "QUEEN,d4,a7", "QUEEN,d4,d7", "KNIGHT,d4,e6",
            "KNIGHT,d4,b3", "KING,a1,b2" })
    void eachPieceMovesAccordingToGeometry(PieceType type, String from, String to) {
        Position start = type == KING ? position(WHITE, "a1 K", "h8 k") : position(WHITE, "a1 K", "h8 k",
                from + " " + Character.toUpperCase(type.symbol()));
        Position next = rules.after(start, Move.of(from, to));
        assertEquals(new Piece(WHITE, type), next.board().at(s(to)).orElseThrow());
        assertTrue(next.board().at(s(from)).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({ "ROOK,d4,e5", "BISHOP,d4,d5", "QUEEN,d4,f5", "KNIGHT,d4,f6", "KING,a1,a3" })
    void geometryRejectsWrongShape(PieceType type, String from, String to) {
        Position start = type == KING ? position(WHITE, "a1 K", "h8 k") : position(WHITE, "a1 K", "h8 k",
                from + " " + Character.toUpperCase(type.symbol()));
        reject(new ChessGame(start), Move.of(from, to), ILLEGAL_GEOMETRY);
    }

    @Test
    void knightJumpsAndCaptureStopsAtTarget() {
        Position next = rules.after(position(WHITE, "a1 K", "h8 k", "d4 N", "e5 P", "e6 p"), Move.of("d4", "e6"));
        assertEquals(new Piece(WHITE, KNIGHT), next.board().at(s("e6")).orElseThrow());
        assertEquals(4, next.board().pieces().size());
        Position rook = rules.after(position(WHITE, "a1 K", "h8 k", "d4 R", "d6 n"), Move.of("d4", "d6"));
        assertEquals(new Piece(WHITE, ROOK), rook.board().at(s("d6")).orElseThrow());
    }

    @Test
    void pawnCannotJumpBlockerCaptureForwardOrDoubleFromLaterRank() {
        reject(new ChessGame(position(WHITE, "a1 K", "h8 k", "e2 P", "e3 n")), Move.of("e2", "e4"), BLOCKED);
        reject(new ChessGame(position(WHITE, "a1 K", "h8 k", "e2 P", "e3 n")), Move.of("e2", "e3"), ILLEGAL_GEOMETRY);
        reject(new ChessGame(position(WHITE, "a1 K", "h8 k", "e3 P")), Move.of("e3", "e5"), ILLEGAL_GEOMETRY);
        reject(new ChessGame(position(WHITE, "a1 K", "h8 k", "e3 P")), Move.of("e3", "e2"), ILLEGAL_GEOMETRY);
    }

    @Test
    void blackPawnMovesDownAndCapturesDiagonally() {
        Position next = rules.after(position(BLACK, "a1 K", "h8 k", "d7 p", "e6 N"), Move.of("d7", "e6"));
        assertEquals(new Piece(BLACK, PAWN), next.board().at(s("e6")).orElseThrow());
        Position doubleMove = rules.after(position(BLACK, "a1 K", "h8 k", "d7 p"), Move.of("d7", "d5"));
        assertEquals(s("d6"), doubleMove.enPassantTarget().orElseThrow());
    }

    @Test
    void pinnedPieceCannotExposeItsKing() {
        // Odejście wieży z e2 odsłania linię e8-e1. Bicie atakującej wieży jest legalne.
        var game = new ChessGame(position(WHITE, "e1 K", "a8 k", "e2 R", "e8 r"));
        reject(game, Move.of("e2", "f2"), KING_IN_CHECK);
        game.play(Move.of("e2", "e8"));
        assertFalse(rules.inCheck(game.snapshot().position().board(), WHITE));
    }

    @Test
    void pinnedOpponentStillAttacksKingDestination() {
        // Czarny skoczek e7 jest związany do króla e8 przez wieżę e1.
        // Mimo to atakuje d5: biały król c4 nie może na to pole wejść.
        var game = new ChessGame(position(WHITE, "c4 K", "e1 R", "e8 k", "e7 n"));
        assertTrue(rules.attacked(game.snapshot().position().board(), s("d5"), BLACK));
        reject(game, Move.of("c4", "d5"), KING_IN_CHECK);
    }

    @Test
    void pawnAttacksEmptyDiagonalButNotEmptyForwardSquare() {
        Board board = position(WHITE, "a1 K", "h8 k", "e5 p").board();
        assertTrue(rules.attacked(board, s("d4"), BLACK));
        assertFalse(rules.attacked(board, s("e4"), BLACK));
        assertFalse(rules.attacked(board, s("d6"), BLACK));
    }

    @Test
    void kingsCannotBecomeAdjacentAndKingCannotBeCaptured() {
        reject(new ChessGame(position(WHITE, "e1 K", "e3 k")), Move.of("e1", "e2"), KING_IN_CHECK);
        // Pozycja analityczna nie obiecuje osiągalności: pozwala sprawdzić
        // osobną ochronę przed „wygraną przez zbicie króla”.
        reject(new ChessGame(position(WHITE, "a1 K", "h8 k", "h1 R")), Move.of("h1", "h8"), KING_CAPTURE);
    }

    @Test
    void kingMayNotCaptureDefendedPiece() {
        reject(new ChessGame(position(WHITE, "e1 K", "h8 k", "e2 r", "b5 b")), Move.of("e1", "e2"), KING_IN_CHECK);
    }

    @Test
    void checkCanBeAnsweredByBlockingOrCapturing() {
        Position checked = position(WHITE, "e1 K", "a8 k", "e8 r", "d2 B");
        assertEquals(GameStatus.CHECK, rules.status(checked));
        assertFalse(rules.inCheck(rules.after(checked, Move.of("d2", "e3")).board(), WHITE));
        reject(new ChessGame(checked), Move.of("d2", "c3"), KING_IN_CHECK);
    }

    @Test
    void foolsMateTerminatesGameAndRejectsFurtherMoves() {
        var game = new ChessGame();
        play(game, "f2 f3", "e7 e5", "g2 g4", "d8 h4");
        assertEquals(GameStatus.CHECKMATE, game.snapshot().status());
        assertEquals(WHITE, game.snapshot().position().turn());
        assertTrue(game.legalMoves().isEmpty());
        assertEquals(4, game.snapshot().history().size());
        reject(game, Move.of("e2", "e3"), GAME_OVER);
    }

    @Test
    void stalemateDiffersFromMateByAttackOnKing() {
        var game = new ChessGame(position(BLACK, "c6 K", "b6 Q", "a8 k"));
        assertEquals(GameStatus.STALEMATE, game.snapshot().status());
        assertFalse(rules.inCheck(game.snapshot().position().board(), BLACK));
        reject(game, Move.of("a8", "a7"), GAME_OVER);
    }

    @ParameterizedTest
    @EnumSource(value = PieceType.class, names = { "QUEEN", "ROOK", "BISHOP", "KNIGHT" })
    void promotionIsOneAtomicMoveWithExplicitChoice(PieceType type) {
        var game = new ChessGame(position(WHITE, "a1 K", "h6 k", "e7 P"));
        reject(game, Move.of("e7", "e8"), PROMOTION_REQUIRED);
        var old = game.snapshot();
        game.play(Move.promote("e7", "e8", type));
        assertEquals(new Piece(WHITE, type), game.snapshot().position().board().at(s("e8")).orElseThrow());
        assertEquals(new Piece(WHITE, PAWN), old.position().board().at(s("e7")).orElseThrow());
        assertEquals(1, game.snapshot().history().size());
    }

    @Test
    void blackPromotionMayCaptureAndChangesCheckImmediately() {
        var game = new ChessGame(position(BLACK, "h1 K", "a8 k", "e2 p", "f1 N"));
        game.play(Move.promote("e2", "f1", ROOK));
        assertEquals(new Piece(BLACK, ROOK), game.snapshot().position().board().at(s("f1")).orElseThrow());
        assertEquals(GameStatus.CHECK, game.snapshot().status());
    }

    @Test
    void unexpectedPromotionAndInvalidPromotionTypeAreRejected() {
        reject(new ChessGame(), Move.promote("e2", "e4", QUEEN), UNEXPECTED_PROMOTION);
        assertThrows(IllegalArgumentException.class, () -> Move.promote("e7", "e8", KING));
        assertThrows(IllegalArgumentException.class, () -> Move.promote("e7", "e8", PAWN));
    }

    @ParameterizedTest
    @CsvSource({ "WHITE,true,e1,g1,h1,f1", "WHITE,false,e1,c1,a1,d1", "BLACK,true,e8,g8,h8,f8",
            "BLACK,false,e8,c8,a8,d8" })
    void castlingMovesBothPiecesAndLosesBothRights(Color color, boolean kingSide, String from, String to,
            String rookFrom, String rookTo) {
        Position position = castlePosition(color, kingSide);
        Position next = rules.after(position, Move.of(from, to));
        assertEquals(new Piece(color, KING), next.board().at(s(to)).orElseThrow());
        assertEquals(new Piece(color, ROOK), next.board().at(s(rookTo)).orElseThrow());
        assertTrue(next.board().at(s(rookFrom)).isEmpty());
        assertFalse(next.rights().allows(color, true));
        assertFalse(next.rights().allows(color, false));
        assertEquals(3, next.board().pieces().size());
    }

    @Test
    void castlingCanBePlayedFromNormalOpening() {
        var game = new ChessGame();
        play(game, "e2 e4", "e7 e5", "g1 f3", "b8 c6", "f1 c4", "g8 f6", "e1 g1");
        assertEquals(new Piece(WHITE, KING), game.snapshot().position().board().at(s("g1")).orElseThrow());
        assertEquals(new Piece(WHITE, ROOK), game.snapshot().position().board().at(s("f1")).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = { "e7", "f7", "g7" })
    void castlingRejectsCheckAtStartTransitOrDestination(String attacker) {
        Position base = castlePosition(WHITE, true);
        var pieces = new HashMap<>(base.board().pieces());
        pieces.put(s(attacker), new Piece(BLACK, ROOK));
        var game = new ChessGame(new Position(new Board(pieces), WHITE, base.rights(), null));
        reject(game, Move.of("e1", "g1"), attacker.equals("g7") ? KING_IN_CHECK : CASTLING_FORBIDDEN);
    }

    @Test
    void queenSideCastlingRequiresBFileEmptyButNotSafe() {
        Position base = castlePosition(WHITE, false);
        var pieces = new HashMap<>(base.board().pieces());
        pieces.put(s("b8"), new Piece(BLACK, ROOK));
        Position attackedB = new Position(new Board(pieces), WHITE, base.rights(), null);
        assertTrue(rules.attacked(attackedB.board(), s("b1"), BLACK));
        assertDoesNotThrow(() -> rules.after(attackedB, Move.of("e1", "c1")));
        pieces.put(s("b1"), new Piece(WHITE, KNIGHT));
        reject(new ChessGame(new Position(new Board(pieces), WHITE, base.rights(), null)), Move.of("e1", "c1"),
                CASTLING_FORBIDDEN);
    }

    @Test
    void returningRookDoesNotRestoreCastlingRight() {
        var game = new ChessGame(castlePosition(WHITE, true));
        play(game, "h1 h2", "e8 e7", "h2 h1", "e7 e8");
        reject(game, Move.of("e1", "g1"), CASTLING_FORBIDDEN);
    }

    @Test
    void attackedRookDoesNotForbidCastlingButMissingRookDoes() {
        Position base = castlePosition(WHITE, true);
        var pieces = new HashMap<>(base.board().pieces());
        pieces.put(s("h7"), new Piece(BLACK, ROOK));
        Position attackedRook = new Position(new Board(pieces), WHITE, base.rights(), null);
        assertTrue(rules.attacked(attackedRook.board(), s("h1"), BLACK));
        assertDoesNotThrow(() -> rules.after(attackedRook, Move.of("e1", "g1")));
        // Sam bit uprawnienia nie zastępuje weryfikacji rzeczywistej wieży.
        reject(new ChessGame(base), Move.of("e1", "c1"), CASTLING_FORBIDDEN);
    }

    @Test
    void doubleCheckCannotBeResolvedByBlockingOnlyOneAttacker() {
        var game = new ChessGame(position(WHITE, "e1 K", "a8 k", "e8 r", "b4 b", "d1 B"));
        // Goniec może zasłonić e8, lecz drugi atak b4-c3-d2-e1 pozostaje.
        // Test wymaga zbadania całej pozycji, nie tylko ostatniego szachującego.
        reject(game, Move.of("d1", "e2"), KING_IN_CHECK);
    }

    @Test
    void returningKingDoesNotRestoreEitherRight() {
        var game = new ChessGame(castlePosition(WHITE, false));
        play(game, "e1 e2", "e8 e7", "e2 e1", "e7 e8");
        assertFalse(game.snapshot().position().rights().allows(WHITE, true));
        reject(game, Move.of("e1", "c1"), CASTLING_FORBIDDEN);
    }

    @Test
    void capturingCornerRookRemovesItsRightOnly() {
        Position base = castlePosition(WHITE, true);
        var pieces = new HashMap<>(base.board().pieces());
        pieces.put(s("g2"), new Piece(BLACK, BISHOP));
        Position next = rules.after(new Position(new Board(pieces), BLACK, base.rights(), null), Move.of("g2", "h1"));
        assertFalse(next.rights().allows(WHITE, true));
        assertTrue(next.rights().allows(WHITE, false));
    }

    @Test
    void analysisPositionsNeverInferHistoricalRights() {
        Position base = castlePosition(WHITE, true);
        reject(new ChessGame(Position.analysis(base.board().pieces(), WHITE)), Move.of("e1", "g1"), CASTLING_FORBIDDEN);
    }

    @Test
    void enPassantRemovesPawnOffDestinationAndKeepsOldSnapshot() {
        var game = new ChessGame();
        play(game, "e2 e4", "a7 a6", "e4 e5", "d7 d5");
        var old = game.snapshot();
        reject(game, Move.of("e5", "e7"), ILLEGAL_GEOMETRY);
        assertEquals(s("d6"), game.snapshot().position().enPassantTarget().orElseThrow());
        game.play(Move.of("e5", "d6"));
        assertTrue(game.snapshot().position().board().at(s("d5")).isEmpty());
        assertEquals(new Piece(WHITE, PAWN), game.snapshot().position().board().at(s("d6")).orElseThrow());
        assertTrue(game.snapshot().position().enPassantTarget().isEmpty());
        assertEquals(new Piece(BLACK, PAWN), old.position().board().at(s("d5")).orElseThrow());
        assertEquals(31, game.snapshot().position().board().pieces().size());
    }

    @Test
    void blackEnPassantUsesOppositeDirection() {
        var game = new ChessGame(position(WHITE, "a1 K", "h8 k", "e2 P", "d4 p"));
        play(game, "e2 e4", "d4 e3");
        assertTrue(game.snapshot().position().board().at(s("e4")).isEmpty());
        assertEquals(new Piece(BLACK, PAWN), game.snapshot().position().board().at(s("e3")).orElseThrow());
    }

    @Test
    void enPassantExpiresAfterAnotherLegalMove() {
        var game = new ChessGame();
        play(game, "e2 e4", "a7 a6", "e4 e5", "d7 d5", "a2 a3", "a6 a5");
        reject(game, Move.of("e5", "d6"), ILLEGAL_GEOMETRY);
    }

    @Test
    void enPassantMustRemoveVictimBeforeCheckingKingSafety() {
        // Na linii a5-h5 stoją oba piony. Po g5xf6 e.p. znika również f5,
        // więc wieża a5 atakuje króla h5. Sama kontrola docelowego pola nie wystarczy.
        var game = new ChessGame(position(BLACK, "h5 K", "h8 k", "g5 P", "f7 p", "a5 r"));
        game.play(Move.of("f7", "f5"));
        reject(game, Move.of("g5", "f6"), KING_IN_CHECK);
    }

    @Test
    void enPassantCanRemoveCheckingPawn() {
        var game = new ChessGame(position(BLACK, "e4 K", "a8 k", "e5 P", "d7 p"));
        game.play(Move.of("d7", "d5"));
        assertEquals(GameStatus.CHECK, game.snapshot().status());
        game.play(Move.of("e5", "d6"));
        assertFalse(rules.inCheck(game.snapshot().position().board(), WHITE));
    }

    @Test
    void initialMoveSetContainsOnlyPawnStepsAndKnightJumps() {
        Set<Move> expected = new HashSet<>();
        for (char file = 'a'; file <= 'h'; file++) {
            expected.add(Move.of(file + "2", file + "3"));
            expected.add(Move.of(file + "2", file + "4"));
        }
        for (String pair : List.of("b1 a3", "b1 c3", "g1 f3", "g1 h3")) {
            String[] parts = pair.split(" ");
            expected.add(Move.of(parts[0], parts[1]));
        }
        List<Move> actual = rules.legalMoves(Position.initial());
        assertEquals(expected, new HashSet<>(actual));
        assertEquals(expected.size(), actual.size());
        assertThrows(UnsupportedOperationException.class, actual::clear);
    }

    @Test
    void generatorIncludesAllFourPromotions() {
        var moves = rules.legalMoves(position(WHITE, "a1 K", "h6 k", "e7 P")).stream().filter(m -> m.from()
                .equals(s("e7"))).toList();
        assertEquals(Set.of(QUEEN, ROOK, BISHOP, KNIGHT), new HashSet<>(moves.stream().map(Move::promotion).toList()));
        assertEquals(4, moves.size());
    }

    @Test
    void snapshotsAndSourceCollectionsCannotMutateGame() {
        var source = new HashMap<>(position(WHITE, "a1 K", "h8 k", "e2 P").board().pieces());
        var game = new ChessGame(Position.analysis(source, WHITE));
        source.clear();
        assertEquals(3, game.snapshot().position().board().pieces().size());
        assertThrows(UnsupportedOperationException.class, () -> game.snapshot().position().board().pieces().clear());
        assertThrows(UnsupportedOperationException.class, () -> game.snapshot().history().add(Move.of("e2", "e4")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "a0", "i1", "A1", "a10", "", " a1" })
    void invalidCoordinatesAreRejectedAtBoundary(String square) {
        assertThrows(IllegalArgumentException.class, () -> Square.parse(square));
    }

    @Test
    void malformedPositionsCannotEnterGame() {
        assertThrows(IllegalArgumentException.class, () -> Position.analysis(Map.of(s("a1"), new Piece(WHITE, KING)),
                WHITE));
        assertThrows(IllegalArgumentException.class, () -> position(WHITE, "a1 K", "b1 K", "h8 k"));
        assertThrows(IllegalArgumentException.class, () -> position(WHITE, "a1 K", "h8 k", "e8 P"));
        assertThrows(IllegalArgumentException.class, () -> new Square(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Square(0, 8));
    }

    /**
     * Perft liczy liście drzewa legalnych ruchów (półruchów). Agregat wykrywa
     * brakujące/nadmiarowe ruchy, ale identyczna suma może ukryć dwa różne błędy.
     * Głębokość 3 pozycji początkowej nie weryfikuje roszady ani promocji;
     * dlatego powyżej istnieją osobne testy tych reguł.
     */
    @ParameterizedTest
    @CsvSource({ "1,20", "2,400", "3,8902" })
    void initialPositionPerft(int depth, long expected) {
        assertEquals(expected, perft(Position.initial(), depth));
    }

    private long perft(Position position, int depth) {
        if (depth == 0)
            return 1;
        long leaves = 0;
        for (Move move : rules.legalMoves(position)) leaves += perft(rules.after(position, move), depth - 1);
        return leaves;
    }

    /**
     * Każda odmowa ma zachować ten sam snapshot, nie tylko liczbę figur.
     */
    private static void reject(ChessGame game, Move move, IllegalMoveException.Reason reason) {
        var before = game.snapshot();
        assertEquals(reason, assertThrows(IllegalMoveException.class, () -> game.play(move)).reason());
        assertSame(before, game.snapshot());
    }

    private static Square s(String text) {
        return Square.parse(text);
    }

    /**
     * Mały język fixture: wielkie litery oznaczają białe, małe czarne.
     */
    private static Position position(Color turn, String... placements) {
        var pieces = new HashMap<Square, Piece>();
        for (String placement : placements) {
            String[] parts = placement.split(" ");
            char symbol = parts[1].charAt(0);
            PieceType type = java.util.Arrays.stream(PieceType.values())
                    .filter(t -> t.symbol() == Character.toLowerCase(symbol)).findFirst().orElseThrow();
            if (pieces.put(s(parts[0]), new Piece(Character.isUpperCase(symbol) ? WHITE : BLACK, type)) != null) {
                throw new IllegalArgumentException("Powtórzone pole fixture");
            }
        }
        return Position.analysis(pieces, turn);
    }

    /**
     * Tylko testy w pakiecie mogą budować historyczny kontekst od zera.
     * Fixture daje prawa obu skrzydeł, ale tylko jedną wieżę: reguły muszą
     * niezależnie sprawdzić obecność wieży. Publiczne analysis nie nadaje praw.
     */
    private static Position castlePosition(Color color, boolean kingSide) {
        int rank = color.homeRank();
        var pieces = new HashMap<Square, Piece>();
        pieces.put(new Square(4, rank), new Piece(color, KING));
        pieces.put(new Square(kingSide ? 7 : 0, rank), new Piece(color, ROOK));
        pieces.put(new Square(4, color.opposite().homeRank()), new Piece(color.opposite(), KING));
        return new Position(new Board(pieces), color, CastlingRights.initial(), null);
    }

    private static void play(ChessGame game, String... moves) {
        for (String move : moves) {
            String[] parts = move.split(" ");
            game.play(Move.of(parts[0], parts[1]));
        }
    }
}
