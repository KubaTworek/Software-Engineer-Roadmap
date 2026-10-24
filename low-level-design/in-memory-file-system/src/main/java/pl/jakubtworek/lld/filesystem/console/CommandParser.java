package pl.jakubtworek.lld.filesystem.console;

import java.util.*;

/**
 * Mała gramatyka argumentów, nie shell. Cudzysłowy chronią spacje i | w ścieżce;
 * wewnątrz nich obsługujemy tylko \" oraz \\. Niezamknięcie cytowania odrzuca
 * całą komendę przed wykonaniem. create/write/find zachowują surowy tekst po
 * pierwszym niecytowanym |, zdejmując najwyżej jedną spację separatora.
 */
public final class CommandParser {

    private CommandParser() {
    }

    public record Command(String name, List<String> arguments) {

        public Command {
            arguments = List.copyOf(arguments);
        }
    }

    public static Command parse(String line) {
        Objects.requireNonNull(line);
        int separator = separator(line);
        String head = separator < 0 ? line : line.substring(0, separator);
        var tokens = tokens(head);
        if (tokens.isEmpty())
            throw new IllegalArgumentException("Pusta komenda");
        String name = tokens.getFirst();
        boolean raw = Set.of("create", "write", "find").contains(name);
        if (raw) {
            if (separator < 0)
                throw new IllegalArgumentException("Wymagany separator: spacja | spacja");
            if (tokens.size() != 2)
                throw new IllegalArgumentException("Wymagana jedna ścieżka przed |");
            String text = line.substring(separator + 1);
            if (text.startsWith(" "))
                text = text.substring(1);
            return new Command(name, List.of(tokens.get(1), text));
        }
        if (separator >= 0)
            throw new IllegalArgumentException("Nieoczekiwany separator |");
        int expected = switch(name) {
            case "help", "q", "live" ->
                0;
            case "mkdir", "read", "ls", "rm", "rm-tree", "save", "use" ->
                1;
            case "move", "copy" ->
                2;
            case "search" ->
                5;
            default ->
                throw new IllegalArgumentException("Nieznana komenda; help");
        };
        if (tokens.size() != expected + 1)
            throw new IllegalArgumentException("Liczba argumentów: " + name + " wymaga " + expected);
        return new Command(name, tokens.subList(1, tokens.size()));
    }

    /**
     * Szukamy tylko granicy nagłówka; payload może zawierać dowolne cudzysłowy.
     */
    private static int separator(String text) {
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted && c == '\\') {
                i++;
                continue;
            }
            if (c == '"')
                quoted = !quoted;
            if (!quoted && c == '|')
                return i;
        }
        return -1;
    }

    private static List<String> tokens(String text) {
        var result = new ArrayList<String>();
        var token = new StringBuilder();
        boolean quoted = false, started = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted && c == '\\') {
                if (++i == text.length())
                    throw new IllegalArgumentException("Urwany escape");
                char next = text.charAt(i);
                if (next != '"' && next != '\\')
                    throw new IllegalArgumentException("Nieobsługiwany escape");
                token.append(next);
                started = true;
            } else if (c == '"') {
                quoted = !quoted;
                started = true;
            } else if (!quoted && Character.isWhitespace(c)) {
                if (started) {
                    result.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
            } else {
                token.append(c);
                started = true;
            }
        }
        if (quoted)
            throw new IllegalArgumentException("Niezamknięty cudzysłów");
        if (started)
            result.add(token.toString());
        return result;
    }
}
