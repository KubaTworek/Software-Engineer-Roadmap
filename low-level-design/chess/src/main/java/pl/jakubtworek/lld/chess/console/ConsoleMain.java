package pl.jakubtworek.lld.chess.console;

import pl.jakubtworek.lld.chess.domain.*;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Scanner;

/**
 * Cienki adapter tekstowy. Parsuje współrzędne i pokazuje kod odmowy; nie
 * rozstrzyga szacha ani legalności. EOF jest poprawnym końcem demo. Pozycja
 * pozostaje w domenie, a konsola nie zamyka współdzielonego System.in/out.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) {
        var out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        run(new InputStreamReader(System.in, StandardCharsets.UTF_8), out);
    }

    /**
     * Wstrzyknięte strumienie pozwalają sprawdzić adapter bez podmiany globalnego System.in.
     */
    public static void run(Readable source, PrintWriter out) {
        var input = new Scanner(source);
        var game = new ChessGame();
        var fen = new FenCodec();
        out.println("Szachy — projekt edukacyjny. Ruch: e2 e4, promocja: e7 e8 Q; help, q.");
        render(game.snapshot(), out);
        while (input.hasNextLine()) {
            String line = input.nextLine().trim();
            if (line.equals("q"))
                break;
            if (line.isEmpty())
                continue;
            try {
                if (line.equals("help")) {
                    out.println("e2 e4 [Q/R/B/N]; board; moves; fen; load <FEN>; new; undo; claims;");
                    out.println("resign white|black; claim white|black threefold|fifty [e2 e4 [Q/R/B/N]];");
                    out.println("perft 0..4; divide 1..4; q");
                } else if (line.equals("board"))
                    render(game.snapshot(), out);
                else if (line.equals("fen"))
                    out.println(fen.format(game.snapshot().position()));
                else if (line.equals("claims"))
                    out.println("Zgłoszenia: " + game.snapshot().availableDrawClaims());
                else if (line.equals("undo"))
                    render(game.undo(), out);
                else if (line.equals("new")) {
                    game = new ChessGame();
                    render(game.snapshot(), out);
                } else if (line.startsWith("load ")) {
                    // Pełna konstrukcja przed przypisaniem: zły FEN nie kasuje dotychczasowej gry.
                    ChessGame loaded = new ChessGame(fen.parse(line.substring(5)));
                    game = loaded;
                    render(game.snapshot(), out);
                } else if (line.equals("moves"))
                    game.legalMoves().stream().sorted(java.util.Comparator.comparing(m -> m.from()
                            .toString() + m.to() + m.promotion()))
                            .forEach(m -> out.println(m.from() + " " + m.to() + (m.promotion() == null ? "" : " " + Character.toUpperCase(m.promotion().symbol()))));
                else {
                    String[] parts = line.split("\\s+");
                    if (parts.length == 2 && parts[0].equals("resign"))
                        render(game.resign(color(parts[1])), out);
                    else if (parts[0].equals("claim") && (parts.length == 3 || parts.length == 5
                            || parts.length == 6)) {
                        DrawClaim claim = switch(parts[2]) {
                            case "threefold" ->
                                DrawClaim.THREEFOLD_REPETITION;
                            case "fifty" ->
                                DrawClaim.FIFTY_MOVE_RULE;
                            default ->
                                throw new IllegalArgumentException("Zgłoszenie: threefold lub fifty");
                        };
                        render(parts.length == 3 ? game.claimDraw(color(parts[1]),
                                claim) : game.claimDraw(color(parts[1]), claim, move(parts, 3)), out);
                    } else if (parts.length == 2 && (parts[0].equals("perft") || parts[0].equals("divide"))) {
                        int depth = Integer.parseInt(parts[1]);
                        // Limit należy do interaktywnego adaptera, nie do reguł szachów.
                        if (depth < 0 || depth > 4)
                            throw new IllegalArgumentException("Głębokość konsoli: 0..4");
                        if (parts[0].equals("perft"))
                            out.println("Perft: " + new Perft().count(game.snapshot().position(), depth));
                        else {
                            var branches = new Perft().divide(game.snapshot().position(), depth);
                            branches.forEach((m,
                                    n) -> out.println(m.from() + " " + m.to() + (m.promotion() == null ? "" : " " + m.promotion()) + ": " + n));
                            out.println("Suma: " + branches.values().stream().mapToLong(Long::longValue).sum());
                        }
                    } else {
                        if (parts.length < 2 || parts.length > 3)
                            throw new IllegalArgumentException("Podaj ruch lub help");
                        render(game.play(move(parts, 0)), out);
                    }
                }
            } catch (IllegalArgumentException ex) {
                out.println("Odmowa: " + ex.getMessage());
            }
        }
        out.flush();
    }

    private static Color color(String text) {
        return switch(text) {
            case "white" ->
                Color.WHITE;
            case "black" ->
                Color.BLACK;
            default ->
                throw new IllegalArgumentException("Gracz: white lub black");
        };
    }

    private static Move move(String[] parts, int offset) {
        PieceType choice = parts.length == offset + 3 ? promotion(parts[offset + 2]) : null;
        return new Move(Square.parse(parts[offset]), Square.parse(parts[offset + 1]), choice);
    }

    private static PieceType promotion(String text) {
        return switch(text.toUpperCase(Locale.ROOT)) {
            case "Q" ->
                PieceType.QUEEN;
            case "R" ->
                PieceType.ROOK;
            case "B" ->
                PieceType.BISHOP;
            case "N" ->
                PieceType.KNIGHT;
            default ->
                throw new IllegalArgumentException("Promocja: Q/R/B/N");
        };
    }

    private static void render(GameSnapshot snapshot, PrintWriter out) {
        for (int rank = 7; rank >= 0; rank--) {
            var line = new StringBuilder().append(rank + 1).append(' ');
            for (int file = 0; file < 8; file++) {
                line.append(snapshot.position().board().at(new Square(file, rank)).map(Piece::symbol).orElse('.'))
                        .append(' ');
            }
            out.println(line);
        }
        out.println("  a b c d e f g h");
        out.println("Tura: " + snapshot.position()
                .turn() + "; status: " + snapshot.status() + "; ruchów: " + snapshot.history().size());
        out.println("Wynik: " + snapshot.result().termination() + "; zwycięzca: " + snapshot.result()
                .winner() + "; powtórzeń: " + snapshot.repetitions() + "; zgłoszenia: " + snapshot.availableDrawClaims());
    }
}
