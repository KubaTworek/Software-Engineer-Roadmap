package pl.jakubtworek.lld.vendingmachine.console;

import pl.jakubtworek.lld.vendingmachine.Coin;
import pl.jakubtworek.lld.vendingmachine.CoinBundle;
import pl.jakubtworek.lld.vendingmachine.ChangePolicy;
import pl.jakubtworek.lld.vendingmachine.ExactChangePlanner;
import pl.jakubtworek.lld.vendingmachine.MinimumCoinChangePlanner;
import pl.jakubtworek.lld.vendingmachine.Money;
import pl.jakubtworek.lld.vendingmachine.Product;
import pl.jakubtworek.lld.vendingmachine.SlotId;
import pl.jakubtworek.lld.vendingmachine.StockItem;
import pl.jakubtworek.lld.vendingmachine.VendingMachine;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Composition root dobiera zasób monet i strategię, a model chroni reguły zakupu.
 * Pierwszy argument zmienia dane, drugi kryterium wyboru reszty. Dzięki temu
 * porównujemy polityki na tych samych danych bez warunków trybu demo w domenie.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws IOException {
        var output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        CoinBundle cash;
        ChangePolicy policy;
        try {
            if (args.length > 2) {
                throw new IllegalArgumentException("Za dużo argumentów");
            }
            policy = switch(args.length < 2 ? "any" : args[1]) {
                case "any" ->
                    new ExactChangePlanner();
                case "min-coins" ->
                    new MinimumCoinChangePlanner();
                default ->
                    throw new IllegalArgumentException("Nieznana strategia reszty");
            };
            cash = switch(args.length == 0 ? "normal" : args[0]) {
                case "normal" ->
                    new CoinBundle(Map.of(Coin.PLN2, 2, Coin.PLN1, 3, Coin.GR50, 4, Coin.GR20, 5, Coin.GR10, 5));
                case "no-change" ->
                    CoinBundle.empty();
                case "greedy-trap" ->
                    new CoinBundle(Map.of(Coin.GR50, 1, Coin.GR20, 3));
                default ->
                    throw new IllegalArgumentException("Nieznany tryb demo");
            };
        } catch (IllegalArgumentException error) {
            output.println(error.getMessage());
            output.println("Użycie: java -jar vending-machine-1.0.0-SNAPSHOT.jar [normal|no-change|greedy-trap] [any|min-coins]");
            return;
        }
        var machine = new VendingMachine(List.of(new StockItem(new SlotId("A1"), new Product("Woda", new Money(250)),
                3), new StockItem(new SlotId("A2"), new Product("Przekąska", new Money(440)), 2),
                new StockItem(new SlotId("A3"), new Product("Sok", new Money(350)), 0)), cash, policy);
        new VendingConsole(machine, new InputStreamReader(System.in, StandardCharsets.UTF_8), output).run();
    }
}
