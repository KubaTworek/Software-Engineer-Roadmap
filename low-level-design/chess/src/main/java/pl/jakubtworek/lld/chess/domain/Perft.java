package pl.jakubtworek.lld.chess.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Narzędzie diagnostyczne liczy legalne sekwencje, ignorując remisy historyczne.
 * Divide lokalizuje różnicę w gałęzi pierwszego ruchu, zamiast podawać tylko sumę.
 * Nie jest niezależnym silnikiem: korzysta z tej samej walidacji co gra.
 */
public final class Perft {

    private final ChessRules rules = new ChessRules();

    public long count(Position position, int depth) {
        if (depth < 0)
            throw new IllegalArgumentException("Głębokość nie może być ujemna");
        if (depth == 0)
            return 1;
        long result = 0;
        for (Move move : rules.legalMoves(position)) result = Math.addExact(result, count(rules.after(position, move),
                depth - 1));
        return result;
    }

    public Map<Move, Long> divide(Position position, int depth) {
        if (depth < 1)
            throw new IllegalArgumentException("Divide wymaga głębokości co najmniej 1");
        var result = new LinkedHashMap<Move, Long>();
        rules.legalMoves(position).stream().sorted(java.util.Comparator.comparing(m -> m.from()
                .toString() + m.to() + m.promotion())).forEach(move -> result.put(move, count(rules.after(position,
                move), depth - 1)));
        return Collections.unmodifiableMap(result);
    }
}
