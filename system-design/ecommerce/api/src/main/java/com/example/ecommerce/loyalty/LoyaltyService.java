package com.example.ecommerce.loyalty;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.loyalty.dto.LoyaltyDtos;
import com.example.ecommerce.outbox.OutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Serwis domenowy programu lojalnościowego.
 *
 * Odpowiada za:
 * - utworzenie konta lojalnościowego użytkownika,
 * - odczyt salda punktów,
 * - naliczanie punktów po opłaconym zamówieniu,
 * - wykorzystanie punktów,
 * - zapis historii operacji w loyalty ledger,
 * - publikację eventów domenowych przez outbox.
 *
 * To jest właściwa warstwa biznesowa programu lojalnościowego.
 * Controller tylko przyjmuje request HTTP i przekazuje go tutaj.
 */
@Service
public class LoyaltyService {

    /**
     * Repozytorium kont lojalnościowych.
     *
     * Każdy użytkownik powinien mieć maksymalnie jedno konto loyalty.
     * Konto przechowuje aktualne saldo punktów oraz tier użytkownika.
     */
    private final LoyaltyAccountRepository accounts;

    /**
     * Repozytorium ledger entries.
     *
     * Ledger to historia zmian punktów.
     *
     * Nie wystarczy trzymać samego salda.
     * W systemie e-commerce potrzebujemy też wiedzieć:
     * - za co punkty zostały naliczone,
     * - kiedy zostały wykorzystane,
     * - z jakim zamówieniem były powiązane,
     * - czy była korekta, wygaśnięcie albo ręczna operacja.
     */
    private final LoyaltyLedgerEntryRepository ledger;

    /**
     * Serwis outbox.
     *
     * Po naliczeniu albo wykorzystaniu punktów zapisujemy event.
     * Inne procesy mogą później zareagować, np.:
     * - wysłać e-mail,
     * - zaktualizować CRM,
     * - zsynchronizować dane z ERP,
     * - policzyć analitykę programu lojalnościowego.
     */
    private final OutboxService outbox;

    /**
     * Konfiguracja przelicznika punktów.
     *
     * Domyślnie:
     * 1 PLN = 1 punkt
     *
     * Wartość pochodzi z konfiguracji:
     * app.stage4.loyalty.points-per-pln
     */
    private final int pointsPerPln;

    /**
     * Constructor injection.
     *
     * Serwis dostaje repozytoria, outbox oraz konfigurowalny przelicznik punktów.
     */
    public LoyaltyService(
            LoyaltyAccountRepository accounts,
            LoyaltyLedgerEntryRepository ledger,
            OutboxService outbox,
            @Value("${app.stage4.loyalty.points-per-pln:1}") int pointsPerPln
    ) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.outbox = outbox;
        this.pointsPerPln = pointsPerPln;
    }

    /**
     * Zwraca konto lojalnościowe użytkownika.
     *
     * Jeśli użytkownik nie ma jeszcze konta loyalty,
     * konto zostanie utworzone automatycznie.
     *
     * To upraszcza frontend:
     * klient może zawsze wywołać GET /api/loyalty/me
     * i dostać poprawną odpowiedź zamiast obsługiwać brak konta.
     */
    @Transactional
    public LoyaltyDtos.LoyaltyAccountResponse account(AppUser user) {
        return toResponse(getOrCreate(user));
    }

    /**
     * Naliczanie punktów za opłacone zamówienie.
     *
     * Ta metoda powinna być wywoływana po sukcesie płatności,
     * czyli wtedy, gdy zamówienie faktycznie zostało opłacone.
     *
     * Flow:
     * 1. Pobierz albo utwórz konto loyalty użytkownika.
     * 2. Przelicz wartość zamówienia na punkty.
     * 3. Dodaj punkty do salda.
     * 4. Zapisz wpis w ledgerze typu EARN.
     * 5. Zapisz event LoyaltyPointsEarned do outboxa.
     *
     * Ważne:
     * Punkty nie powinny być naliczane przy samym utworzeniu zamówienia,
     * bo użytkownik może jeszcze nie zapłacić.
     */
    @Transactional
    public void earnForOrder(
            AppUser user,
            Long orderId,
            BigDecimal amount
    ) {
        LoyaltyAccount account = getOrCreate(user);

        /*
         * Proste przeliczenie MVP:
         * część całkowita kwoty * pointsPerPln.
         *
         * Przykład:
         * amount = 129.99
         * pointsPerPln = 1
         * points = 129
         *
         * W systemie produkcyjnym można uwzględnić:
         * - walutę,
         * - promocje,
         * - mnożniki dla tierów,
         * - wykluczone kategorie,
         * - punkty tylko od wartości netto,
         * - punkty dopiero po okresie zwrotu.
         */
        int points = amount.intValue() * pointsPerPln;

        /*
         * Aktualizujemy saldo punktów na koncie.
         *
         * LoyaltyAccount odpowiada też za przeliczenie tieru,
         * np. BRONZE/SILVER/GOLD/PLATINUM.
         */
        account.addPoints(points);

        /*
         * Zapis historii operacji.
         *
         * Ledger pozwala później wyjaśnić klientowi,
         * skąd wzięły się punkty na koncie.
         */
        ledger.save(
                new LoyaltyLedgerEntry(
                        account,
                        LoyaltyLedgerType.EARN,
                        points,
                        orderId,
                        "Order paid"
                )
        );

        /*
         * Event domenowy o naliczeniu punktów.
         *
         * Przydatny dla:
         * - notification-service,
         * - CRM,
         * - analytics,
         * - dashboardów marketingowych.
         */
        outbox.saveEvent(
                "LoyaltyAccount",
                account.getId().toString(),
                "LoyaltyPointsEarned",
                Map.of(
                        "userId", user.getId(),
                        "orderId", orderId,
                        "points", points
                )
        );
    }

    /**
     * Wykorzystuje punkty użytkownika.
     *
     * Flow:
     * 1. Pobierz albo utwórz konto loyalty.
     * 2. Spróbuj odjąć punkty z salda.
     * 3. Jeśli saldo jest za małe, zwróć błąd 400.
     * 4. Zapisz wpis w ledgerze typu REDEEM.
     * 5. Zapisz event LoyaltyPointsRedeemed do outboxa.
     * 6. Zwróć aktualny stan konta.
     *
     * W tej wersji redeem jest manualny.
     * W pełnym systemie powinien być połączony z checkout pricingiem,
     * żeby wykorzystane punkty obniżały wartość zamówienia.
     */
    @Transactional
    public LoyaltyDtos.LoyaltyAccountResponse redeem(
            AppUser user,
            LoyaltyDtos.RedeemRequest request
    ) {
        LoyaltyAccount account = getOrCreate(user);

        try {
            /*
             * Logika sprawdzenia salda jest w encji LoyaltyAccount.
             *
             * Jeśli użytkownik próbuje wykorzystać więcej punktów niż ma,
             * encja rzuca IllegalArgumentException.
             */
            account.redeemPoints(request.points());
        } catch (IllegalArgumentException e) {
            /*
             * Zamieniamy wyjątek domenowy na błąd API.
             *
             * Dla klienta to błąd requestu, np. za małe saldo punktów.
             */
            throw ApiException.badRequest(e.getMessage());
        }

        /*
         * Ledger zapisuje historię wykorzystania punktów.
         *
         * orderId pozwala powiązać wykorzystanie punktów z konkretnym zamówieniem.
         */
        ledger.save(
                new LoyaltyLedgerEntry(
                        account,
                        LoyaltyLedgerType.REDEEM,
                        request.points(),
                        request.orderId(),
                        "Manual redeem"
                )
        );

        /*
         * Event domenowy po wykorzystaniu punktów.
         *
         * Może zostać użyty przez e-mail/CRM/analitykę.
         */
        outbox.saveEvent(
                "LoyaltyAccount",
                account.getId().toString(),
                "LoyaltyPointsRedeemed",
                Map.of(
                        "userId", user.getId(),
                        "orderId", request.orderId(),
                        "points", request.points()
                )
        );

        return toResponse(account);
    }

    /**
     * Pobiera istniejące konto loyalty albo tworzy nowe.
     *
     * To pozwala traktować konto lojalnościowe jako zasób tworzony leniwie.
     *
     * Pierwszy kontakt użytkownika z programem loyalty automatycznie zakłada konto,
     * zamiast wymagać osobnej rejestracji.
     */
    private LoyaltyAccount getOrCreate(AppUser user) {
        return accounts.findByUserId(user.getId())
                .orElseGet(() -> accounts.save(new LoyaltyAccount(user)));
    }

    /**
     * Mapuje encję LoyaltyAccount na DTO odpowiedzi API.
     *
     * DTO zawiera tylko dane potrzebne klientowi:
     * - accountId,
     * - userId,
     * - aktualne saldo punktów,
     * - tier użytkownika.
     */
    private LoyaltyDtos.LoyaltyAccountResponse toResponse(LoyaltyAccount account) {
        return new LoyaltyDtos.LoyaltyAccountResponse(
                account.getId(),
                account.getUser().getId(),
                account.getPointsBalance(),
                account.getTier()
        );
    }
}