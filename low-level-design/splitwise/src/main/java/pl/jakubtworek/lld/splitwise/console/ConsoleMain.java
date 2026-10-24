package pl.jakubtworek.lld.splitwise.console;

import pl.jakubtworek.lld.splitwise.ExpenseGroup;
import pl.jakubtworek.lld.splitwise.Member;
import pl.jakubtworek.lld.splitwise.MemberId;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Miejsce składania aplikacji: jedna grupa demo bez zależności czasu i usług zewnętrznych.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws IOException {
        var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        if (args.length != 0) {
            output.println("Użycie: java -jar splitwise-1.0.0-SNAPSHOT.jar");
            return;
        }
        var group = new ExpenseGroup(List.of(new Member(new MemberId("U1"), "Ala"), new Member(new MemberId("U2"),
                "Bartek"), new Member(new MemberId("U3"), "Celina")));
        new ExpenseConsole(group, new InputStreamReader(System.in, StandardCharsets.UTF_8), output).run();
    }
}
