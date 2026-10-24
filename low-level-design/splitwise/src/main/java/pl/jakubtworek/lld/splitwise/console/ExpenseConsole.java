package pl.jakubtworek.lld.splitwise.console;

import pl.jakubtworek.lld.splitwise.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;

/**
 * Adapter parsuje grosze i punkty procentowe, ale nie liczy sald ani reszt.
 * Wstrzykiwane strumienie umożliwiają test rozmowy bez globalnego terminala.
 * Zakończenie rozmowy nie cofa wydatków; każda komenda jest kompletną operacją.
 */
public final class ExpenseConsole {

    private final ExpenseGroup group;
    private final BufferedReader input;
    private final PrintWriter output;

    public ExpenseConsole(ExpenseGroup group, Reader input, PrintWriter output) {
        this.group = Objects.requireNonNull(group, "group");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Rozliczenia: help, status, history, equal/percent/exact, warianty -key, settle, settle-key, reverse, q");
        output.flush();
        String line;
        while ((line = input.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.strip().split("\\s+");
            try {
                if (parts[0].equals("q")) {
                    arity(parts, 1);
                    break;
                }
                switch(parts[0]) {
                    case "help" ->
                        {
                            arity(parts, 1);
                            output.println("equal PŁATNIK GROSZE OPIS OSOBA... | percent/exact PŁATNIK GROSZE OPIS OSOBA=WARTOŚĆ... | equal-key/percent-key/exact-key KLUCZ ... | settle OD DO GROSZE | settle-key KLUCZ OD DO GROSZE | reverse KLUCZ PŁATNIK UUID POWÓD | status | history | q");
                        }
                    case "status" ->
                        {
                            arity(parts, 1);
                            status();
                        }
                    case "history" ->
                        {
                            arity(parts, 1);
                            history();
                        }
                    case "equal", "percent", "exact", "equal-key", "percent-key", "exact-key" ->
                        {
                            int offset = parts[0].endsWith("-key") ? 1 : 0;
                            if (parts.length < 5 + offset) {
                                throw new IllegalArgumentException("Brak danych podziału");
                            }
                            String mode = parts[0].replace("-key", "");
                            SplitPolicy policy = switch(mode) {
                                case "equal" ->
                                    new EqualSplit(Arrays.stream(parts).skip(4 + offset).map(MemberId::new).toList());
                                case "percent" ->
                                    percentages(parts, 4 + offset);
                                default ->
                                    exact(parts, 4 + offset);
                            };
                            var payer = new MemberId(parts[1 + offset]);
                            var total = new Money(Long.parseLong(parts[2 + offset]));
                            var expense = offset == 0 ? group.addExpense(payer, total, policy,
                                    parts[3]) : group.addExpense(new OperationKey(parts[1]), payer, total, policy,
                                    parts[4]);
                            output.println("Zapisano wydatek: " + expense.id()
                                    .value() + ", kwota=" + expense.total() + ", udziały=" + shares(expense));
                        }
                    case "settle", "settle-key" ->
                        {
                            int offset = parts[0].equals("settle-key") ? 1 : 0;
                            arity(parts, 4 + offset);
                            var from = new MemberId(parts[1 + offset]);
                            var to = new MemberId(parts[2 + offset]);
                            var amount = new Money(Long.parseLong(parts[3 + offset]));
                            var settlement = offset == 0 ? group.settle(from, to,
                                    amount) : group.settle(new OperationKey(parts[1]), from, to, amount);
                            output.println("Zapisano spłatę: " + settlement.id()
                                    .value() + ", kwota=" + settlement.amount());
                        }
                    case "reverse" ->
                        {
                            arity(parts, 5);
                            var reversal = group.reverseExpense(new OperationKey(parts[1]), new MemberId(parts[2]),
                                    EntryId.parse(parts[3]), parts[4]);
                            output.println("Zapisano korektę: " + reversal.id()
                                    .value() + ", wydatek=" + reversal.expenseId().value());
                        }
                    default ->
                        throw new IllegalArgumentException("Nieznana komenda");
                }
            } catch (SplitException error) {
                output.println("Odmowa " + error.reason() + ": " + error.getMessage());
            } catch (IllegalArgumentException | ArithmeticException error) {
                output.println("Błąd danych: " + error.getMessage());
            }
            output.flush();
        }
        output.println("Koniec demo");
        output.flush();
    }

    private static PercentageSplit percentages(String[] parts, int start) {
        var points = new HashMap<MemberId, Integer>();
        for (int index = start; index < parts.length; index++) {
            String[] pair = parts[index].split("=", -1);
            if (pair.length != 2) {
                throw new IllegalArgumentException("Udział wymaga OSOBA=PUNKTY");
            }
            if (points.putIfAbsent(new MemberId(pair[0]), Integer.parseInt(pair[1])) != null) {
                throw new IllegalArgumentException("Powtórzona osoba w podziale");
            }
        }
        return new PercentageSplit(points);
    }

    private static ExactSplit exact(String[] parts, int start) {
        var shares = new HashMap<MemberId, Money>();
        for (int index = start; index < parts.length; index++) {
            String[] pair = parts[index].split("=", -1);
            if (pair.length != 2) {
                throw new IllegalArgumentException("Udział wymaga OSOBA=GROSZE");
            }
            if (shares.putIfAbsent(new MemberId(pair[0]), new Money(Long.parseLong(pair[1]))) != null) {
                throw new IllegalArgumentException("Powtórzona osoba w podziale");
            }
        }
        return new ExactSplit(shares);
    }

    private void status() {
        var view = group.snapshot();
        view.members().values().stream().sorted(Comparator.comparing(member -> member.id().value()))
                .forEach(member -> output.println(member.id()
                .value() + " " + member.name() + ": " + view.balanceOf(member.id())));
    }

    private void history() {
        for (LedgerEntry entry : group.snapshot().entries()) {
            switch(entry) {
                case Expense expense ->
                    output.println("Wydatek " + expense.id()
                            .value() + " " + expense.description() + " płatnik=" + expense.payer()
                            .value() + " " + expense.total() + " " + shares(expense));
                case Settlement settlement ->
                    output.println("Spłata " + settlement.id().value() + " " + settlement.from()
                            .value() + " -> " + settlement.to().value() + " " + settlement.amount());
                case ExpenseReversal reversal ->
                    output.println("Korekta " + reversal.id().value() + " wydatek=" + reversal.expenseId()
                            .value() + " płatnik=" + reversal.actor().value() + " " + reversal.reason());
            }
        }
    }

    private static String shares(Expense expense) {
        return expense.shares().entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().value()))
                .map(entry -> entry.getKey().value() + "=" + entry.getValue()).toList().toString();
    }

    private static void arity(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("Niepoprawna liczba argumentów");
        }
    }
}
