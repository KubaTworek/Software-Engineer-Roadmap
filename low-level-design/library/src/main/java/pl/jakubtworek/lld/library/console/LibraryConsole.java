package pl.jakubtworek.lld.library.console;

import pl.jakubtworek.lld.library.BookId;
import pl.jakubtworek.lld.library.CopyId;
import pl.jakubtworek.lld.library.Library;
import pl.jakubtworek.lld.library.LibraryException;
import pl.jakubtworek.lld.library.MemberId;
import pl.jakubtworek.lld.library.LoanId;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.time.DateTimeException;
import java.util.Comparator;
import java.util.Objects;

/**
 * Adapter zamienia tekst na typowane argumenty, ale nie wylicza limitu ani
 * kolejności odbioru. Przy odmowie pokazuje kod domenowy. Wstrzykiwane strumienie
 * pozwalają przetestować rozmowę bez globalnego System.in i System.out.
 * Nie zamykamy zasobów należących do wywołującego; IOException propaguje dalej.
 */
public final class LibraryConsole {

    private final Library library;
    private final BufferedReader input;
    private final PrintWriter output;

    public LibraryConsole(Library library, Reader input, PrintWriter output) {
        this.library = Objects.requireNonNull(library, "library");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Biblioteka: help, status, borrow M1 B1, return/renew M1 C1 LOAN_UUID, reserve M1 B1, cancel M1 B1, q");
        output.flush();
        String line;
        while ((line = input.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            var parts = line.strip().split("\\s+");
            try {
                if (parts[0].equals("q")) {
                    arity(parts, 1);
                    break;
                }
                switch(parts[0]) {
                    case "help" ->
                        {
                            arity(parts, 1);
                            output.println("borrow CZYTELNIK TYTUŁ | return/renew CZYTELNIK EGZEMPLARZ LOAN_UUID | reserve/cancel CZYTELNIK TYTUŁ | status | q");
                        }
                    case "status" ->
                        {
                            arity(parts, 1);
                            status();
                        }
                    case "borrow" ->
                        {
                            arity(parts, 3);
                            var loan = library.borrow(new MemberId(parts[1]), new BookId(parts[2]));
                            output.println("Wypożyczono: " + loan.copyId().value() + ", loan=" + loan.id()
                                    .value() + ", termin: " + loan.dueAt());
                        }
                    case "return" ->
                        {
                            arity(parts, 4);
                            var loan = library.returnCopy(new MemberId(parts[1]), new CopyId(parts[2]),
                                    LoanId.parse(parts[3]));
                            output.println("Zwrócono: " + loan.copyId().value());
                        }
                    case "renew" ->
                        {
                            arity(parts, 4);
                            var loan = library.renew(new MemberId(parts[1]), new CopyId(parts[2]),
                                    LoanId.parse(parts[3]));
                            output.println("Przedłużono: " + loan.copyId().value() + ", loan=" + loan.id()
                                    .value() + ", termin: " + loan.dueAt());
                        }
                    case "reserve" ->
                        {
                            arity(parts, 3);
                            library.reserve(new MemberId(parts[1]), new BookId(parts[2]));
                            output.println("Zapisano rezerwację");
                        }
                    case "cancel" ->
                        {
                            arity(parts, 3);
                            library.cancelReservation(new MemberId(parts[1]), new BookId(parts[2]));
                            output.println("Anulowano rezerwację");
                        }
                    default ->
                        throw new IllegalArgumentException("Nieznana komenda");
                }
            } catch (LibraryException error) {
                output.println("Odmowa " + error.reason() + ": " + error.getMessage());
            } catch (IllegalArgumentException | DateTimeException error) {
                output.println("Błąd danych: " + error.getMessage());
            }
            output.flush();
        }
        // Zakończenie rozmowy nie jest zwrotem książek. Każde polecenie było
        // kompletną operacją; nie ma otwartej sesji do anulowania jak w automacie.
        output.println("Koniec demo");
        output.flush();
    }

    private void status() {
        // Jeden spójny widok dla całej prezentacji.
        var view = library.snapshot();
        view.books().values().stream().sorted(Comparator.comparing(book -> book.id().value())).forEach(book -> {
            var copies = view.availableCopies(book.id()).stream().map(copy -> copy.id().value()).toList();
            var queue = view.queueFor(book.id()).stream().map(MemberId::value).toList();
            output.println(book.id().value() + " " + book.title() + ": wolne=" + copies + ", kolejka=" + queue);
        });
        view.members().values().stream().sorted(Comparator.comparing(member -> member.id().value()))
                .forEach(member -> output.println(member.id()
                .value() + " " + member.name() + ": " + view.activeLoans(member.id()).size() + "/" + member.membership()
                .loanLimit() + " " + member.membership()));
        view.loans().values().stream().sorted(Comparator.comparing(loan -> loan.copyId().value()))
                .forEach(loan -> output.println(loan.copyId().value() + " -> " + loan.memberId()
                .value() + ", loan=" + loan.id()
                .value() + ", termin=" + loan.dueAt() + ", przedłużone=" + loan.renewed()));
    }

    private static void arity(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("Niepoprawna liczba argumentów");
        }
    }
}
