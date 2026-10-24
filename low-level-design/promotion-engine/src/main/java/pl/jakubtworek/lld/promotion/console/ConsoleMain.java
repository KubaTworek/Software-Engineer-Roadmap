package pl.jakubtworek.lld.promotion.console;

import pl.jakubtworek.lld.promotion.domain.*;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Adapter jest właścicielem roboczego koszyka. Buduje niemutowalny kontekst
 * przy wycenie, lecz sam nie rozstrzyga zgodności promocji. Nową zawartość
 * sprawdza PRZED publikacją, dlatego błędna ilość/cena/suma nie usuwa danych.
 * Strumieni dostarczonych przez użytkownika nie zamykamy; EOF kończy sesję.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) {
        run(new InputStreamReader(System.in, StandardCharsets.UTF_8), new PrintWriter(System.out, true,
                StandardCharsets.UTF_8));
    }

    public static void run(Readable source, PrintWriter out) {
        var input = new Scanner(source);
        var lines = new TreeMap<String, CartLine>();
        var coupons = new TreeSet<String>();
        CustomerTier tier = CustomerTier.STANDARD;
        var engine = DemoPromotions.engine();
        Instant now = Instant.parse("2026-10-21T12:00:00Z");
        SelectionPolicy policy = new PrioritySelection();
        out.println("Silnik promocji — projekt edukacyjny, PLN, limit rabatu 25.00 PLN. help, q.");
        while (input.hasNextLine()) {
            String line = input.nextLine().trim();
            if (line.equals("q"))
                break;
            if (line.isEmpty())
                continue;
            try {
                String[] parts = line.split("\\s+");
                if (line.equals("help")) {
                    out.println("add SKU category price quantity; remove SKU; tier standard|vip;");
                    out.println("coupon CODE; uncoupon CODE; basket; promotions; quote; new; q");
                    out.println("policy priority|best-single; at <Instant ISO-8601>; czas demo początkowo 2026-10-21T12:00:00Z");
                } else if (parts.length == 2 && parts[0].equals("policy")) {
                    SelectionPolicy candidate = switch(parts[1]) {
                        case "priority" ->
                            new PrioritySelection();
                        case "best-single" ->
                            new BestSingleSelection();
                        default ->
                            throw new IllegalArgumentException("Polityka: priority lub best-single");
                    };
                    var configured = new PromotionEngine(DemoPromotions.all(), Money.pln("25"), Clock.fixed(now,
                            ZoneOffset.UTC), candidate);
                    policy = candidate;
                    engine = configured;
                    out.println("Polityka: " + parts[1]);
                } else if (parts.length == 2 && parts[0].equals("at")) {
                    Instant candidate = Instant.parse(parts[1]);
                    var configured = new PromotionEngine(DemoPromotions.all(), Money.pln("25"), Clock.fixed(candidate,
                            ZoneOffset.UTC), policy);
                    now = candidate;
                    engine = configured;
                    out.println("Chwila wyceny: " + now);
                } else if (parts.length == 5 && parts[0].equals("add")) {
                    var item = new CartLine(parts[1], parts[2], Money.pln(parts[3]), Integer.parseInt(parts[4]));
                    if (lines.containsKey(item.sku()))
                        throw new IllegalArgumentException("SKU już istnieje; usuń je przed zmianą");
                    var candidate = new ArrayList<>(lines.values());
                    candidate.add(item);
                    // Również suma całego koszyka musi się mieścić w long.
                    new Basket(candidate);
                    lines.put(item.sku(), item);
                    out.println("Dodano: " + item.sku());
                } else if (parts.length == 2 && parts[0].equals("remove")) {
                    if (lines.remove(parts[1]) == null)
                        throw new IllegalArgumentException("Nieznane SKU");
                    out.println("Usunięto: " + parts[1]);
                } else if (parts.length == 2 && parts[0].equals("tier")) {
                    tier = CustomerTier.valueOf(parts[1].toUpperCase(Locale.ROOT));
                    out.println("Segment: " + tier);
                } else if (parts.length == 2 && parts[0].equals("coupon")) {
                    coupons.add(PricingContext.normalizeCoupon(parts[1]));
                    out.println("Kupony: " + coupons);
                } else if (parts.length == 2 && parts[0].equals("uncoupon")) {
                    if (!coupons.remove(PricingContext.normalizeCoupon(parts[1])))
                        throw new IllegalArgumentException("Brak kuponu");
                    out.println("Kupony: " + coupons);
                } else if (line.equals("basket")) {
                    lines.values().forEach(item -> out.println(item.sku() + " " + item.category() + " " + item.quantity() + " × " + item.unitPrice()));
                    out.println("Suma koszyka: " + new Basket(new ArrayList<>(lines.values())).subtotal());
                } else if (line.equals("promotions")) {
                    out.println("FLASH50: priorytet 200, kupon FLASH50, 50%, EXCLUSIVE");
                    out.println("VIP10: priorytet 100, segment VIP, 10%, STACKABLE");
                    out.println("WELCOME15: priorytet 90, kupon WELCOME i koszyk >= 50 PLN, 15 PLN, wyklucza BOOKS20");
                    out.println("BOOKS20: priorytet 80, kategoria books i koszyk >= 100 PLN, 20 PLN, STACKABLE");
                    out.println("BOOKS10: priorytet 70, kupon BOOKS10, 10% wyłącznie od books");
                    out.println("VALID5: priorytet 60, kupon VALID5, 5 PLN, [2026-10-21T00:00:00Z, 2026-10-22T00:00:00Z)");
                } else if (line.equals("quote")) {
                    var context = new PricingContext(new Basket(new ArrayList<>(lines.values())), tier, coupons);
                    PricingResult result = engine.quote(context);
                    out.println("Przed: " + result.subtotal() + "; rabat: " + result.discount() + "; do zapłaty: " + result.payable());
                    result.decisions().forEach(d -> {
                        out.println(d.id() + ": " + d.status() + "; propozycja: " + d.requested().map(Money::toString)
                                .orElse("nie wyliczano") + "; zastosowano: " + d.applied() + "; saldo: " + d.remainingAfter() + d.blocker().map(b -> "; blokuje: " + b).orElse(""));
                        renderCondition(d.qualification(), "  ", out);
                        d.calculation().ifPresent(c -> out.println("  podstawa: " + c.base() + "; przycięcia: " + c.clippingReasons() + "; alokacja " + (d.status() == DecisionStatus.NOT_SELECTED ? "potencjalna: " : "zatwierdzana: ") + new TreeMap<>(c.allocation())));
                    });
                    result.lines().forEach(l -> out.println("Linia " + l.sku() + ": rabat " + l.discount() + "; należność " + l.payable()));
                } else if (line.equals("new")) {
                    lines.clear();
                    coupons.clear();
                    tier = CustomerTier.STANDARD;
                    out.println("Nowy koszyk");
                } else
                    throw new IllegalArgumentException("Nieznana komenda lub liczba argumentów; użyj help");
            } catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException ex) {
                out.println("Odmowa: " + ex.getMessage());
            }
        }
        out.flush();
    }

    private static void renderCondition(ConditionEvaluation evaluation, String indent, PrintWriter out) {
        out.println(indent + evaluation.outcome() + " " + evaluation.description());
        evaluation.children().forEach(child -> renderCondition(child, indent + "  ", out));
    }
}
