package pl.jakubtworek.lld.editor.console;

import pl.jakubtworek.lld.editor.EditorHistory;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Mały adapter do eksperymentu z historią. Argumenty tekstowe są resztą linii,
 * bez własnego języka cytowania. Domenę można testować bez konsoli i zegara.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws IOException {
        run(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true));
    }

    public static void run(BufferedReader input, PrintWriter output) throws IOException {
        var editor = new EditorHistory("");
        output.println("insert OFFSET TEXT | delete OFFSET LENGTH | undo | redo [ID] | checkout ID | branches | show | q");
        String line;
        while ((line = input.readLine()) != null) {
            if (line.strip().equals("q")) {
                break;
            }
            String[] parts = line.strip().split("\\s+", 3);
            try {
                switch(parts[0]) {
                    case "insert" ->
                        editor.insert(Integer.parseInt(parts[1]), parts.length == 3 ? parts[2] : "");
                    case "delete" ->
                        editor.delete(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    case "undo" ->
                        editor.undo();
                    case "redo" ->
                        {
                            if (parts.length == 1) {
                                editor.redo();
                            } else {
                                editor.redo(Long.parseLong(parts[1]));
                            }
                        }
                    case "checkout" ->
                        editor.checkout(Long.parseLong(parts[1]));
                    case "branches" ->
                        output.println(editor.branches());
                    case "show" ->
                        {
                        }
                    default ->
                        throw new IllegalArgumentException("Nieznane polecenie");
                }
                output.println("v" + editor.snapshot().version() + ": " + editor.snapshot().text());
            } catch (RuntimeException exception) {
                output.println("Odmowa: " + exception.getMessage());
            }
        }
    }
}
