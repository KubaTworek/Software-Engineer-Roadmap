package pl.jakubtworek.lld.vendingmachine.console;

import pl.jakubtworek.lld.vendingmachine.Coin;
import pl.jakubtworek.lld.vendingmachine.CoinBundle;
import pl.jakubtworek.lld.vendingmachine.MachinePhase;
import pl.jakubtworek.lld.vendingmachine.SlotId;
import pl.jakubtworek.lld.vendingmachine.VendingException;
import pl.jakubtworek.lld.vendingmachine.VendingMachine;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/**
 * Adapter tekstowy zależny wyłącznie od publicznego API. Parsuje kod i nominał,
 * lecz o legalności polecenia, reszcie i zapasie decyduje domena. Reader oraz
 * PrintWriter są przekazane z zewnątrz, więc test rozmowy nie używa klawiatury.
 * q/EOF świadomie anuluje otwartą sesję, pokazując jej zwrot przed końcem demo.
 * Błąd odczytu propagujemy; PrintWriter.checkError() nie jest tu kontrolowany.
 */
public final class VendingConsole {

    private final VendingMachine machine;
    private final BufferedReader input;
    private final PrintWriter output;

    public VendingConsole(VendingMachine machine, Reader input, PrintWriter output) {
        this.machine = Objects.requireNonNull(machine, "machine");
        this.input = new BufferedReader(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
    }

    public void run() throws IOException {
        output.println("Automat vendingowy — demo edukacyjne. Wpisz help.");
        while (true) {
            output.print("> ");
            output.flush();
            String line = input.readLine();
            if (line == null || line.strip().equalsIgnoreCase("q")) {
                if (machine.snapshot().phase() != MachinePhase.IDLE) {
                    var refund = machine.cancel();
                    output.println("Zwrot przy zakończeniu: " + refund + " = " + refund.total());
                }
                output.println("Koniec demo.");
                output.flush();
                return;
            }
            try {
                execute(line.strip().split("\\s+"));
            } catch (VendingException error) {
                output.println("Odmowa [" + error.reason() + "]: " + error.getMessage());
            } catch (IllegalArgumentException | ArithmeticException error) {
                // Błąd składni lub zakresu nie jest udanym zakupem. Nie łapiemy
                // każdego RuntimeException: błąd algorytmu ma pozostać widoczny.
                output.println("Niepoprawna wartość lub polecenie: " + error.getMessage());
            }
        }
    }

    private void execute(String[] parts) {
        switch(parts[0].toLowerCase(Locale.ROOT)) {
            case "help" ->
                {
                    requireSize(parts, 1);
                    output.println("status | select KOD | insert NOMINAL_GROSZ | buy | cancel | restock KOD ILOSC | load NOMINAL_GROSZ | q");
                    output.println("Monety w groszach: 1, 2, 5, 10, 20, 50, 100, 200, 500.");
                }
            case "status" ->
                {
                    requireSize(parts, 1);
                    var view = machine.snapshot();
                    output.println("Faza " + view.phase() + ", kredyt " + view.credit() + ", wybór " + view.selectedSlot());
                    view.stock().values().stream().sorted(Comparator.comparing(item -> item.slotId().value()))
                            .forEach(item -> output.println(item.slotId().value() + ": " + item.product()
                            .name() + ", cena " + item.product().price() + ", sztuk " + item.quantity()));
                    output.println("Depozyt " + view.escrow() + ", kaseta " + view.cash() + ", przychód " + view.revenue());
                }
            case "select" ->
                {
                    requireSize(parts, 2);
                    machine.select(new SlotId(parts[1]));
                    output.println("Wybrano produkt " + parts[1].toUpperCase(Locale.ROOT));
                }
            case "insert" ->
                {
                    requireSize(parts, 2);
                    machine.insert(Coin.fromGrosz(Integer.parseInt(parts[1])));
                    output.println("Kredyt " + machine.snapshot().credit());
                }
            case "buy" ->
                {
                    requireSize(parts, 1);
                    var receipt = machine.buy();
                    output.println("Wydano: " + receipt.product().name() + ", cena " + receipt.product()
                            .price() + ", reszta " + receipt.change() + " = " + receipt.change().total());
                }
            case "cancel" ->
                {
                    requireSize(parts, 1);
                    var refund = machine.cancel();
                    output.println("Zwrot: " + refund + " = " + refund.total());
                }
            case "restock" ->
                {
                    requireSize(parts, 3);
                    machine.restock(new SlotId(parts[1]), Integer.parseInt(parts[2]));
                    output.println("Uzupełniono produkt.");
                }
            case "load" ->
                {
                    requireSize(parts, 2);
                    machine.loadCash(CoinBundle.of(Coin.fromGrosz(Integer.parseInt(parts[1]))));
                    output.println("Uzupełniono kasetę.");
                }
            default ->
                output.println("Nieznane polecenie. Wpisz help.");
        }
    }

    private static void requireSize(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("Niewłaściwa liczba argumentów; użyj help");
        }
    }
}
