package pl.jakubtworek.lld.chess.domain;

import java.util.HashMap;
import java.util.Objects;
import static pl.jakubtworek.lld.chess.domain.PieceType.*;

/**
 * Granica tekst/pozycja: sześć pól standardowego FEN, bez Chess960. Najpierw
 * składnia, potem lokalna spójność. Nie dowodzimy, że istnieje legalna partia
 * prowadząca do ustawienia (np. że promowane figury zgadzają się z liczbą bić).
 * FEN przenosi liczniki, ale NIE historię powtórzeń ani wynik/rezygnację.
 */
public final class FenCodec {

    public Position parse(String text) {
        if (text == null)
            throw new IllegalArgumentException("Brak FEN");
        String[] fields = text.trim().split("\\s+");
        if (fields.length != 6)
            throw new IllegalArgumentException("FEN wymaga sześciu pól");
        Board board = board(fields[0]);
        Color turn = switch(fields[1]) {
            case "w" ->
                Color.WHITE;
            case "b" ->
                Color.BLACK;
            default ->
                throw new IllegalArgumentException("Kolor FEN: w lub b");
        };
        CastlingRights rights = rights(fields[2]);
        Square ep = fields[3].equals("-") ? null : Square.parse(fields[3]);
        long halfmove = number(fields[4], false), fullmove = number(fields[5], true);
        Position result = new Position(board, turn, rights, ep, halfmove, fullmove);
        validateContext(result);
        return result;
    }

    public String format(Position position) {
        Objects.requireNonNull(position);
        var text = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = position.board().at(new Square(file, rank)).orElse(null);
                if (piece == null)
                    empty++;
                else {
                    if (empty > 0)
                        text.append(empty);
                    empty = 0;
                    text.append(piece.symbol());
                }
            }
            if (empty > 0)
                text.append(empty);
            if (rank > 0)
                text.append('/');
        }
        text.append(position.turn() == Color.WHITE ? " w " : " b ");
        var rights = new StringBuilder();
        if (position.rights().whiteKingSide())
            rights.append('K');
        if (position.rights().whiteQueenSide())
            rights.append('Q');
        if (position.rights().blackKingSide())
            rights.append('k');
        if (position.rights().blackQueenSide())
            rights.append('q');
        text.append(rights.isEmpty() ? "-" : rights).append(' ');
        // Standard FEN zachowuje cel po podwójnym ruchu również wtedy, gdy
        // nikt nie może bić. Klucz powtórzeń normalizuje go inaczej!
        return text.append(position.enPassantTarget().map(Square::toString).orElse("-")).append(' ')
                .append(position.halfmoveClock()).append(' ').append(position.fullmoveNumber()).toString();
    }

    private Board board(String placement) {
        String[] ranks = placement.split("/", -1);
        if (ranks.length != 8)
            throw new IllegalArgumentException("FEN wymaga ośmiu rzędów");
        var pieces = new HashMap<Square, Piece>();
        for (int row = 0; row < 8; row++) {
            int file = 0;
            boolean previousDigit = false;
            for (char symbol : ranks[row].toCharArray()) {
                if (symbol >= '1' && symbol <= '8') {
                    if (previousDigit)
                        throw new IllegalArgumentException("Połącz puste pola w jedną cyfrę");
                    file += symbol - '0';
                    previousDigit = true;
                } else {
                    if (file >= 8)
                        throw new IllegalArgumentException("Za szeroki rząd FEN");
                    // Samo toLowerCase zaakceptowałoby np. znak Kelvina K jako K.
                    // Format wymienia konkretne symbole ASCII, a nie podobne litery Unicode.
                    if ("KQRBNPkqrbnp".indexOf(symbol) < 0)
                        throw new IllegalArgumentException("Nieznana figura FEN");
                    PieceType type = switch(Character.toLowerCase(symbol)) {
                        case 'k' ->
                            KING;
                        case 'q' ->
                            QUEEN;
                        case 'r' ->
                            ROOK;
                        case 'b' ->
                            BISHOP;
                        case 'n' ->
                            KNIGHT;
                        case 'p' ->
                            PAWN;
                        default ->
                            throw new IllegalArgumentException("Nieznana figura FEN");
                    };
                    pieces.put(new Square(file++, 7 - row),
                            new Piece(Character.isUpperCase(symbol) ? Color.WHITE : Color.BLACK, type));
                    previousDigit = false;
                }
                if (file > 8)
                    throw new IllegalArgumentException("Za szeroki rząd FEN");
            }
            if (file != 8)
                throw new IllegalArgumentException("Rząd FEN musi mieć osiem pól");
        }
        return new Board(pieces);
    }

    private CastlingRights rights(String text) {
        if (!text.matches("-|K?Q?k?q?") || text.isEmpty()) {
            throw new IllegalArgumentException("Prawa FEN: KQkq w tej kolejności lub -");
        }
        return new CastlingRights(text.contains("K"), text.contains("Q"), text.contains("k"), text.contains("q"));
    }

    private long number(String text, boolean positive) {
        // Ten sam zakres co Position pozwala ponownie odczytać wyeksportowane
        // liczniki również po przekroczeniu zakresu int. Przekroczenie long
        // jest jawną odmową, nigdy zawinięciem do wartości ujemnej.
        if (!text.matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("Niepoprawny licznik FEN");
        try {
            long value = Long.parseLong(text);
            if (positive && value == 0)
                throw new IllegalArgumentException("Numer ruchu zaczyna się od 1");
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Licznik FEN przekracza zakres long", ex);
        }
    }

    private void validateContext(Position position) {
        for (Color color : Color.values()) {
            for (boolean kingSide : new boolean[] { true, false }) {
                if (!position.rights().allows(color, kingSide))
                    continue;
                if (!position.board().at(new Square(4, color.homeRank())).filter(new Piece(color, KING)::equals)
                        .isPresent() || !position.board().at(new Square(kingSide ? 7 : 0, color.homeRank()))
                        .filter(new Piece(color, ROOK)::equals).isPresent()) {
                    throw new IllegalArgumentException("Prawo roszady bez króla i wieży na polach początkowych");
                }
            }
        }
        position.enPassantTarget().ifPresent(ep -> {
            Color mover = position.turn().opposite();
            int expectedRank = position.turn() == Color.WHITE ? 5 : 2;
            if (ep.rank() != expectedRank || position.halfmoveClock() != 0 || position.board().at(ep).isPresent()
                    || !position.board().at(new Square(ep.file(), ep.rank() + mover.forward())).filter(new Piece(mover,
                    PAWN)::equals).isPresent() || position.board().at(new Square(ep.file(),
                    ep.rank() - mover.forward())).isPresent()) {
                throw new IllegalArgumentException("Niespójny kontekst en passant");
            }
        });
        // Strona, która właśnie wykonała ruch, nie mogła zostawić króla w szachu.
        // Odrzuca to również sąsiadujących królów, ale nadal nie dowodzi osiągalności.
        if (new ChessRules().inCheck(position.board(), position.turn().opposite())) {
            throw new IllegalArgumentException("Król strony niemającej ruchu jest szachowany");
        }
    }
}
