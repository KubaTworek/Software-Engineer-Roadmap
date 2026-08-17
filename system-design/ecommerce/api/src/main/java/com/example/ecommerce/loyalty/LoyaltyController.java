package com.example.ecommerce.loyalty;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.loyalty.dto.LoyaltyDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller odpowiedzialny za API programu lojalnościowego klienta.
 *
 * W aplikacji e-commerce loyalty program pozwala użytkownikowi:
 * - sprawdzić aktualne saldo punktów,
 * - sprawdzić swój tier, np. BRONZE/SILVER/GOLD/PLATINUM,
 * - wykorzystać punkty.
 *
 * Controller nie zawiera logiki naliczania ani rozliczania punktów.
 * Deleguje wszystko do LoyaltyService.
 */
@RestController
@RequestMapping("/api/loyalty")
public class LoyaltyController {

    /**
     * Serwis programu lojalnościowego.
     *
     * Odpowiada za właściwą logikę:
     * - utworzenie konta lojalnościowego, jeśli jeszcze nie istnieje,
     * - odczyt salda punktów,
     * - naliczanie punktów po zakupie,
     * - wykorzystanie punktów,
     * - zapis operacji w loyalty ledger,
     * - publikację eventów przez outbox.
     */
    private final LoyaltyService loyalty;

    /**
     * Constructor injection.
     *
     * Controller potrzebuje tylko LoyaltyService.
     * Nie powinien bezpośrednio operować na repozytoriach punktów.
     */
    public LoyaltyController(LoyaltyService loyalty) {
        this.loyalty = loyalty;
    }

    /**
     * Zwraca konto lojalnościowe aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/loyalty/me
     *
     * @AuthenticationPrincipal AppUser user:
     * użytkownik jest pobierany z kontekstu bezpieczeństwa.
     *
     * Dzięki temu klient API nie przekazuje userId.
     * To ważne, bo saldo punktów jest prywatnym zasobem użytkownika.
     *
     * LoyaltyService może przy tym:
     * - znaleźć istniejące konto lojalnościowe,
     * - albo utworzyć je automatycznie przy pierwszym wejściu.
     */
    @GetMapping("/me")
    public LoyaltyDtos.LoyaltyAccountResponse account(
            @AuthenticationPrincipal AppUser user
    ) {
        return loyalty.account(user);
    }

    /**
     * Wykorzystuje punkty lojalnościowe użytkownika.
     *
     * Endpoint:
     * POST /api/loyalty/redeem
     *
     * Request powinien zawierać liczbę punktów do wykorzystania
     * oraz opcjonalny kontekst, np. orderId.
     *
     * Kluczowe:
     * - użytkownik pochodzi z tokena, nie z request body,
     * - LoyaltyService musi sprawdzić, czy użytkownik ma wystarczające saldo,
     * - operacja powinna zostać zapisana w loyalty ledger,
     * - saldo punktów powinno zostać zmniejszone atomowo.
     *
     * W pełnym checkout flow redeem punktów powinien być powiązany
     * z kalkulacją ceny zamówienia, żeby punkty realnie obniżały total.
     */
    @PostMapping("/redeem")
    public LoyaltyDtos.LoyaltyAccountResponse redeem(
            @AuthenticationPrincipal AppUser user,
            @RequestBody LoyaltyDtos.RedeemRequest request
    ) {
        return loyalty.redeem(user, request);
    }
}