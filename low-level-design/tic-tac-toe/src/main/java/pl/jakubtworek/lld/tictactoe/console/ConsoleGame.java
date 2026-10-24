package pl.jakubtworek.lld.tictactoe.console;

import pl.jakubtworek.lld.tictactoe.GameStatus;
import pl.jakubtworek.lld.tictactoe.IllegalMoveException;
import pl.jakubtworek.lld.tictactoe.Position;
import pl.jakubtworek.lld.tictactoe.TicTacToeGame;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Objects;

/**
 * Adapter zamienia tekst na polecenie domenowe i wynik na komunikat.
 * Korzysta wyłącznie z PUBLICZNEGO API modelu: osobny pakiet wymusza to
 * podczas kompilacji, także po ukryciu konstruktora GameSnapshot.
 *
 * Reader i PrintWriter wstrzykujemy zamiast czytać globalny System.in.
 * Dzięki temu test może przeprowadzić rozmowę z konsolą bez uruchamiania
 * procesu i podmieniania globalnych strumieni. Adapter nie zamyka zasobów
 * przekazanych przez wywołującego. Błąd odczytu propaguje jako IOException.
 * PrintWriter przechowuje błędy zapisu we własnej fladze; to proste demo nie
 * kontroluje checkError() i nie gwarantuje dostarczenia tekstu na urządzenie.
 *
 * Cel edukacyjny: zależności przekazane w konstruktorze są wystarczającą formą
 * dependency injection. Kontener DI nie jest warunkiem testowalności.
 * Test malformedAndIllegalInputDoesNotConsumeTurnAndGameCanContinue rozdziela
 * błędy składni wejścia od poprawnie zapisanych, lecz niedozwolonych ruchów.
 */
public final class ConsoleGame {

    private final TicTacToeGame game;
    private final BufferedReader input;
    private final PrintWriter output;
    private final BoardRenderer renderer = new BoardRenderer();

    public ConsoleGame(TicTacToeGame game, Reader input, PrintWriter output) {
        this.game = Objects.requireNonNull(game, "game");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Kółko i krzyżyk. Podaj: wiersz kolumna (od 0). q kończy demo.");
        while (game.snapshot().status() == GameStatus.IN_PROGRESS) {
            output.print(renderer.render(game.snapshot().board()));
            output.print("Ruch " + game.snapshot().nextPlayer().orElseThrow() + ": ");
            // Prompt nie kończy się nową linią. Flush przed blokującym odczytem
            // pozwala go zobaczyć użytkownikowi, zanim ma podać odpowiedź.
            output.flush();
            String line = input.readLine();
            if (line == null || line.strip().equalsIgnoreCase("q")) {
                // EOF i rezygnacja kończą adapter, a nie partię. Nie dodajemy
                // do domeny nowego stanu tylko dla cyklu życia konsoli.
                output.println("Zakończono demo; partia pozostaje bez zmian.");
                output.flush();
                return;
            }
            Position position;
            try {
                position = parse(line);
            } catch (IllegalArgumentException error) {
                // Ten catch obejmuje tylko parsowanie. Nie przechwytujemy tu
                // wszystkich wyjątków z gry, aby błąd modelu nie został
                // mylnie pokazany jako „niepoprawna liczba” wpisana w konsoli.
                output.println("Błędny format. Podaj dwie liczby całkowite: wiersz kolumna.");
                continue;
            }
            try {
                // Konsola podpowiada właściwego gracza, lecz nie waliduje
                // zajętości ani granic. Reguły nadal należą do domeny.
                game.play(game.snapshot().nextPlayer().orElseThrow(), position);
            } catch (IllegalMoveException error) {
                output.println("Ruch odrzucony [" + error.reason() + "]: " + error.getMessage());
            }
        }
        output.print(renderer.render(game.snapshot().board()));
        output.println(game.snapshot().status().winner().map(mark -> "Wygrywa " + mark + "!").orElse("Remis."));
        output.flush();
    }

    private static Position parse(String line) {
        // Parser sprawdza strukturę tekstu i zakres typu int, nie zakres pola
        // dla konkretnej planszy. Np. -1 0 przechodzi parsowanie, a dopiero
        // domena odmawia ruchu z OUT_OF_BOUNDS. Pozwala to zachować jedno
        // źródło reguły, używane także przez przyszły adapter innego rodzaju.
        String[] parts = line.strip().split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Oczekiwano dwóch współrzędnych");
        }
        return new Position(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
