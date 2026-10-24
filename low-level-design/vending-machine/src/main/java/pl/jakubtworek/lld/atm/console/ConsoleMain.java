package pl.jakubtworek.lld.atm.console;

import pl.jakubtworek.lld.atm.*;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.Duration;

/**
 * Składa fikcyjne konta, karty, PIN-y i kasetę. Nie pobiera konfiguracji z sieci ani sekretów środowiska.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws java.io.IOException {
        var out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        Duration ttl;
        try {
            if (args.length > 1) {
                throw new IllegalArgumentException("Nadmiar argumentów");
            }
            ttl = args.length == 1 ? Duration.ofSeconds(Long.parseLong(args[0])) : Duration.ofMinutes(5);
            if (ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("TTL musi być dodatni");
            }
        } catch (IllegalArgumentException | ArithmeticException error) {
            out.println("Użycie: java -cp vending-machine-1.0.0-SNAPSHOT.jar pl.jakubtworek.lld.atm.console.ConsoleMain [TTL_SEKUNDY>0]");
            return;
        }
        var a1 = new AccountId("A1");
        var a2 = new AccountId("A2");
        var c1 = new CardId("C1");
        var c2 = new CardId("C2");
        var cash = new CashInventory(Map.of(Banknote.PLN_10, 10, Banknote.PLN_20, 5, Banknote.PLN_50, 2,
                Banknote.PLN_100, 1, Banknote.PLN_200, 1));
        var auth = new DemoAuthenticator(Map.of(c1, Pin.parse("1234"), c2, Pin.parse("4321")));
        var atm = new Atm(List.of(new Account(a1, new Money(100000)), new Account(a2, new Money(40000))), Map.of(c1, a1,
                c2, a2), cash, auth, Clock.systemUTC(), ttl);
        new AtmConsole(atm, new InputStreamReader(System.in, StandardCharsets.UTF_8), out).run();
    }
}
