package pl.jakubtworek.lld.tictactoe.console;

import pl.jakubtworek.lld.tictactoe.GameRules;
import pl.jakubtworek.lld.tictactoe.TicTacToeGame;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Punkt składania aplikacji (composition root): tutaj wybieramy reguły,
 * tworzymy model i łączymy go z adapterem oraz standardowymi strumieniami.
 * Cel edukacyjny: zależności powstają na brzegu aplikacji, zamiast być
 * wyszukiwane globalnie wewnątrz logiki gry. Dzięki temu sam model można
 * uruchomić w testach bez konsoli, konfiguracji frameworka i procesów potomnych.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws IOException {
        // Jawne UTF-8 stabilizuje kodowanie polskich komunikatów; zgodne
        // kodowanie musi mieć też terminal. Nie zamykamy System.in/System.out.
        var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        GameRules rules;
        try {
            if (args.length != 0 && args.length != 2) {
                throw new IllegalArgumentException("Podaj zero albo dwa argumenty");
            }
            rules = args.length == 0 ? GameRules.classic() : new GameRules(Integer.parseInt(args[0]),
                    Integer.parseInt(args[1]));
        } catch (IllegalArgumentException error) {
            // Składnia liczb i kontrakt N/K muszą być poprawne przed startem.
            // Walidację relacji między N i K pozostawiamy GameRules; nie
            // kopiujemy jej w CLI. Jest to komunikat demo, bez osobnej polityki
            // kodów zakończenia procesu dla narzędzia używanego w automatyzacji.
            output.println("Niepoprawne parametry: " + error.getMessage());
            output.println("Użycie: java -jar tic-tac-toe-1.0.0-SNAPSHOT.jar [N K]");
            return;
        }
        new ConsoleGame(new TicTacToeGame(rules), new InputStreamReader(System.in, StandardCharsets.UTF_8), output)
                .run();
    }
}
