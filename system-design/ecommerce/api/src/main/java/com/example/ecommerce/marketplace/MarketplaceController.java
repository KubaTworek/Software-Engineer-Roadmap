package com.example.ecommerce.marketplace;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.marketplace.dto.MarketplaceDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller odpowiedzialny za publiczne API marketplace.
 *
 * Marketplace rozszerza klasyczny sklep o możliwość obsługi wielu sprzedawców.
 *
 * Ten controller udostępnia operacje:
 * - utworzenia konta sprzedawcy przez zalogowanego użytkownika,
 * - pobrania listy sprzedawców.
 *
 * Controller nie zawiera logiki biznesowej marketplace.
 * Wszystko deleguje do MarketplaceService.
 */
@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {

    /**
     * Serwis marketplace.
     *
     * Odpowiada za właściwą logikę:
     * - utworzenie konta sprzedawcy,
     * - sprawdzenie, czy użytkownik nie ma już konta seller,
     * - ustawienie statusu sprzedawcy,
     * - zapis eventów marketplace do outboxa,
     * - mapowanie encji Seller na DTO.
     */
    private final MarketplaceService marketplace;

    /**
     * Constructor injection.
     *
     * Controller potrzebuje tylko MarketplaceService.
     * Nie powinien samodzielnie używać repozytorium SellerRepository.
     */
    public MarketplaceController(MarketplaceService marketplace) {
        this.marketplace = marketplace;
    }

    /**
     * Tworzy konto sprzedawcy dla aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * POST /api/marketplace/sellers
     *
     * @AuthenticationPrincipal AppUser user:
     * użytkownik pochodzi z kontekstu bezpieczeństwa.
     * Nie przekazujemy ownerId w request body, żeby klient nie mógł utworzyć
     * konta sprzedawcy w imieniu innego użytkownika.
     *
     * @Valid @RequestBody:
     * waliduje dane requestu, np. displayName i slug.
     *
     * Typowy flow:
     * 1. Użytkownik zakłada konto seller.
     * 2. Konto dostaje status PENDING_VERIFICATION.
     * 3. Admin może później aktywować sprzedawcę.
     *
     * To oddziela samo zgłoszenie sprzedawcy od dopuszczenia go do sprzedaży.
     */
    @PostMapping("/sellers")
    public MarketplaceDtos.SellerResponse createSeller(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody MarketplaceDtos.CreateSellerRequest request
    ) {
        return marketplace.createSeller(user, request);
    }

    /**
     * Zwraca listę sprzedawców marketplace.
     *
     * Endpoint:
     * GET /api/marketplace/sellers
     *
     * W tej wersji zwracana jest pełna lista sellerów.
     *
     * W produkcyjnym systemie warto rozdzielić:
     * - publiczną listę aktywnych sprzedawców,
     * - adminową listę wszystkich sprzedawców,
     * - filtrowanie po statusie,
     * - paginację.
     *
     * Obecnie metoda jest prosta i deleguje całość do MarketplaceService.
     */
    @GetMapping("/sellers")
    public List<MarketplaceDtos.SellerResponse> sellers() {
        return marketplace.sellers();
    }
}