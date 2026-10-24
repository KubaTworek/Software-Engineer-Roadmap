package pl.jakubtworek.lld.atm.console;

import pl.jakubtworek.lld.atm.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adapter parsuje polecenia i formatuje kwoty; nie liczy podziału banknotów.
 * Zakończenie rozmowy (q, EOF lub wyjątek) kończy tylko sesję otwartą przez
 * tę rozmowę, na podstawie zapamiętanego ID; nie zwraca karty innego klienta,
 * ale nigdy nie kompensuje przyjętej wypłaty. Strumienie są cudzym zasobem,
 * więc ich nie zamykamy. Wejście tekstowe demo nie ukrywa wpisywania PIN-u.
 */
public final class AtmConsole {

    private final Atm atm;
    private final BufferedReader input;
    private final PrintWriter output;
    private SessionId sessionId;

    public AtmConsole(Atm atm, Reader input, PrintWriter output) {
        this.atm = Objects.requireNonNull(atm, "atm");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Bankomat edukacyjny: help, insert, pin, balance, withdraw, history, eject, status, q");
        output.flush();
        try {
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
                                output.println("insert KARTA | pin [UUID_SESJI] CZTERY_CYFRY | balance [UUID_SESJI] | withdraw GROSZE | withdraw-key KLUCZ GROSZE | withdraw-for UUID_SESJI KLUCZ GROSZE | history [UUID_SESJI] | eject [UUID_SESJI] | status | q");
                            }
                        case "insert" ->
                            {
                                arity(parts, 2);
                                sessionId = atm.insertCard(new CardId(parts[1]));
                                output.println("Włożono kartę, sesja=" + sessionId.value());
                            }
                        case "pin" ->
                            {
                                optionalArity(parts, 2, 3);
                                var id = parts.length == 3 ? SessionId.parse(parts[1]) : token();
                                var result = atm.enterPin(id, Pin.parse(parts[parts.length - 1]));
                                if (result == AuthenticationResult.CARD_EJECTED && id.equals(sessionId)) {
                                    sessionId = null;
                                }
                                output.println("Autoryzacja: " + result);
                            }
                        case "balance" ->
                            {
                                optionalArity(parts, 1, 2);
                                output.println("Saldo: " + atm.balance(parts.length == 2 ? SessionId.parse(parts[1]) : token()));
                            }
                        case "withdraw", "withdraw-key", "withdraw-for" ->
                            {
                                int expected = parts[0].equals("withdraw") ? 2 : parts[0]
                                        .equals("withdraw-key") ? 3 : 4;
                                arity(parts, expected);
                                var amount = new Money(Long.parseLong(parts[expected - 1]));
                                var receipt = expected == 2 ? atm.withdraw(token(),
                                        amount) : atm.withdraw(expected == 4 ? SessionId.parse(parts[1]) : token(),
                                        new OperationKey(parts[expected - 2]), amount);
                                output.println("Wypłata " + receipt.id() + ": " + receipt.amount() + ", banknoty=" + cash(receipt.cash()) + ", saldo=" + receipt.balanceAfter());
                            }
                        case "history" ->
                            {
                                optionalArity(parts, 1, 2);
                                atm.history(parts.length == 2 ? SessionId.parse(parts[1]) : token())
                                        .forEach(receipt -> output.println("Potwierdzenie " + receipt.id() + " " + receipt.amount() + " " + cash(receipt.cash()) + " saldo=" + receipt.balanceAfter()));
                            }
                        case "eject" ->
                            {
                                optionalArity(parts, 1, 2);
                                var id = parts.length == 2 ? SessionId.parse(parts[1]) : token();
                                atm.ejectCard(id);
                                if (id.equals(sessionId)) {
                                    sessionId = null;
                                }
                                output.println("Zwrócono kartę");
                            }
                        case "status" ->
                            {
                                arity(parts, 1);
                                String status = switch(atm.snapshot().session()) {
                                    case Session.Idle ignored ->
                                        "IDLE";
                                    case Session.CardInserted inserted ->
                                        "CARD_INSERTED błędy=" + inserted.failedAttempts();
                                    case Session.Authenticated ignored ->
                                        "AUTHENTICATED";
                                };
                                if (atm.snapshot().session() instanceof Session.Active active) {
                                    status += " id=" + active.id().value() + " ważna-do=" + active.expiresAt();
                                }
                                output.println("Sesja: " + status);
                            }
                        default ->
                            throw new IllegalArgumentException("Nieznana komenda");
                    }
                } catch (AtmException error) {
                    if (error.reason() == AtmException.Reason.SESSION_EXPIRED) {
                        sessionId = null;
                    }
                    output.println("Odmowa " + error.reason() + ": " + error.getMessage());
                } catch (IllegalArgumentException | java.time.DateTimeException | ArithmeticException error) {
                    output.println("Błąd danych: " + error.getMessage());
                }
                output.flush();
            }
        } finally {
            // Sesja rozmowy nie zostaje aktywna po awarii wejścia. To osobna
            // operacja kończąca sesję, nie cofnięcie historii, konta lub kasety.
            if (sessionId != null) {
                try {
                    atm.ejectCard(sessionId);
                    output.println("Zwrócono kartę na koniec rozmowy");
                } catch (AtmException error) {
                    if (error.reason() != AtmException.Reason.SESSION_EXPIRED
                            && error.reason() != AtmException.Reason.STALE_SESSION
                            && error.reason() != AtmException.Reason.INVALID_SESSION_STATE) {
                        throw error;
                    }
                }
                sessionId = null;
            }
            output.flush();
        }
        output.println("Koniec demo");
        output.flush();
    }

    // Nie odczytujemy nowego ID z aktualnego snapshotu jako obejścia ochrony.
    // Adapter wiąże skrócone komendy wyłącznie z tokenem otrzymanym przy insert.
    private SessionId token() {
        if (sessionId == null) {
            throw new AtmException(AtmException.Reason.INVALID_SESSION_STATE, "Brak sesji tej rozmowy");
        }
        return sessionId;
    }

    private static void optionalArity(String[] parts, int first, int second) {
        if (parts.length != first && parts.length != second) {
            throw new IllegalArgumentException("Niepoprawna liczba argumentów");
        }
    }

    private static String cash(CashBundle bundle) {
        return bundle.notes().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(Comparator.comparingInt(Banknote::grosz).reversed()))
                .map(item -> item.getKey().grosz() / 100 + " PLN x " + item.getValue())
                .collect(Collectors.joining(", "));
    }

    private static void arity(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("Niepoprawna liczba argumentów");
        }
    }
}
