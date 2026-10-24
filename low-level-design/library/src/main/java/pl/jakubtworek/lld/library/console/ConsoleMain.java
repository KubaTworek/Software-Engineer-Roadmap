package pl.jakubtworek.lld.library.console;

import pl.jakubtworek.lld.library.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;

/**
 * Miejsce składania aplikacji: stałe dane demo i rzeczywisty zegar UTC.
 * Domena nie rozróżnia uruchomienia konsolowego od testu z Clock.fixed.
 * Dwa egzemplarze B1 pozwalają obserwować uprawnienia wielu osób z kolejki.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws IOException {
        var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        if (args.length != 0) {
            output.println("Użycie: java -jar library-1.0.0-SNAPSHOT.jar");
            return;
        }
        var b1 = new BookId("B1");
        var b2 = new BookId("B2");
        var b3 = new BookId("B3");
        var library = new Library(List.of(new Book(b1, "Java — podstawy", "Autor A"), new Book(b2,
                "Projektowanie obiektowe", "Autor B"), new Book(b3, "Algorytmy", "Autor C")),
                List.of(new BookCopy(new CopyId("C1"), b1), new BookCopy(new CopyId("C2"), b1),
                new BookCopy(new CopyId("C3"), b2), new BookCopy(new CopyId("C4"), b3)),
                List.of(new Member(new MemberId("M1"), "Ala", Membership.STANDARD), new Member(new MemberId("M2"),
                "Bartek", Membership.STANDARD), new Member(new MemberId("M3"), "Celina", Membership.EXTENDED)),
                Clock.systemUTC());
        new LibraryConsole(library, new InputStreamReader(System.in, StandardCharsets.UTF_8), output).run();
    }
}
