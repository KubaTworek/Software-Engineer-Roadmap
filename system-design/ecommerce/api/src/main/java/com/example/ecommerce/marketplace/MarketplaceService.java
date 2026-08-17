package com.example.ecommerce.marketplace;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.marketplace.dto.MarketplaceDtos;
import com.example.ecommerce.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Serwis domenowy marketplace.
 *
 * Marketplace pozwala użytkownikom zakładać konta sprzedawców,
 * a administratorowi aktywować tych sprzedawców po weryfikacji.
 *
 * Ten serwis odpowiada za:
 * - utworzenie konta sprzedawcy,
 * - blokadę wielu kont seller dla jednego użytkownika,
 * - aktywację sprzedawcy,
 * - pobranie listy sellerów,
 * - publikację eventów marketplace przez outbox,
 * - mapowanie encji Seller na DTO.
 */
@Service
public class MarketplaceService {

    /**
     * Repozytorium sprzedawców.
     *
     * Przechowuje konta marketplace seller.
     *
     * Seller jest powiązany z AppUser jako właścicielem konta.
     */
    private final SellerRepository sellers;

    /**
     * Serwis outbox.
     *
     * Po operacjach marketplace zapisujemy eventy domenowe.
     *
     * Dzięki temu inne procesy mogą zareagować asynchronicznie, np.:
     * - onboarding sprzedawcy,
     * - weryfikacja KYC/KYB,
     * - CRM,
     * - notification-service,
     * - ERP,
     * - analityka marketplace.
     */
    private final OutboxService outbox;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje repozytorium SellerRepository oraz OutboxService.
     */
    public MarketplaceService(
            SellerRepository sellers,
            OutboxService outbox
    ) {
        this.sellers = sellers;
        this.outbox = outbox;
    }

    /**
     * Tworzy konto sprzedawcy dla aktualnie zalogowanego użytkownika.
     *
     * Flow:
     * 1. Sprawdź, czy użytkownik nie ma już konta seller.
     * 2. Utwórz nowego Sellera z ownerem, displayName i slugiem.
     * 3. Zapisz sellera w bazie.
     * 4. Zapisz event SellerCreated do outboxa.
     * 5. Zwróć DTO odpowiedzi.
     *
     * Ważne:
     * owner nie pochodzi z request body.
     * Ownerem jest AppUser z kontekstu bezpieczeństwa.
     *
     * Dzięki temu klient API nie może utworzyć konta sprzedawcy
     * w imieniu innego użytkownika.
     */
    @Transactional
    public MarketplaceDtos.SellerResponse createSeller(
            AppUser owner,
            MarketplaceDtos.CreateSellerRequest request
    ) {
        /*
         * Jeden użytkownik może mieć tylko jedno konto sprzedawcy.
         *
         * To zabezpiecza przed przypadkowym lub celowym tworzeniem wielu seller accounts
         * dla tego samego usera.
         *
         * W bardziej rozbudowanym marketplace można dopuścić wiele sklepów per user,
         * ale wtedy model powinien to robić jawnie, np. przez organizacje/teams.
         */
        sellers.findByOwnerId(owner.getId()).ifPresent(existing -> {
            throw ApiException.conflict("User already owns a seller account");
        });

        /*
         * Tworzymy konto sprzedawcy.
         *
         * W encji Seller domyślny status to zwykle PENDING_VERIFICATION,
         * więc sprzedawca nie powinien od razu móc aktywnie sprzedawać,
         * dopóki admin go nie zatwierdzi.
         */
        Seller seller = sellers.save(
                new Seller(
                        owner,
                        request.displayName(),
                        request.slug()
                )
        );

        /*
         * Event SellerCreated.
         *
         * Może uruchomić downstream procesy:
         * - wysłanie maila potwierdzającego zgłoszenie,
         * - zadanie weryfikacji sprzedawcy,
         * - synchronizację z CRM,
         * - utworzenie profilu rozliczeniowego.
         */
        outbox.saveEvent(
                "Seller",
                seller.getId().toString(),
                "SellerCreated",
                Map.of(
                        "sellerId", seller.getId(),
                        "ownerId", owner.getId(),
                        "slug", seller.getSlug()
                )
        );

        return toResponse(seller);
    }

    /**
     * Aktywuje konto sprzedawcy.
     *
     * To operacja adminowa.
     *
     * Typowy proces:
     * 1. Użytkownik tworzy konto seller.
     * 2. Konto ma status PENDING_VERIFICATION.
     * 3. Admin lub proces weryfikacyjny sprawdza dane.
     * 4. Seller zostaje aktywowany.
     * 5. Od tego momentu może wystawiać produkty.
     *
     * W tej metodzie nie ma adnotacji bezpieczeństwa,
     * więc kontrola dostępu powinna być zapewniona na poziomie AdminController
     * albo konfiguracji Spring Security.
     */
    @Transactional
    public MarketplaceDtos.SellerResponse activateSeller(Long sellerId) {
        /*
         * Jeśli seller nie istnieje, zwracamy 404.
         */
        Seller seller = sellers.findById(sellerId)
                .orElseThrow(() -> ApiException.notFound("Seller not found"));

        /*
         * Zmieniamy status sellera na ACTIVE.
         *
         * Sama logika zmiany statusu jest zamknięta w encji Seller.
         */
        seller.activate();

        /*
         * Event SellerActivated.
         *
         * Przydatny np. do:
         * - wysłania maila do sprzedawcy,
         * - uruchomienia onboarding checklist,
         * - synchronizacji z ERP/CRM,
         * - nadania uprawnień w panelu sprzedawcy.
         */
        outbox.saveEvent(
                "Seller",
                seller.getId().toString(),
                "SellerActivated",
                Map.of("sellerId", seller.getId())
        );

        return toResponse(seller);
    }

    /**
     * Zwraca listę wszystkich sprzedawców.
     *
     * W tej wersji metoda zwraca wszystkich sellerów niezależnie od statusu.
     *
     * W produkcyjnym API warto rozdzielić:
     * - publiczną listę tylko aktywnych sellerów,
     * - adminową listę wszystkich sellerów,
     * - filtrowanie po statusie,
     * - paginację.
     */
    @Transactional(readOnly = true)
    public List<MarketplaceDtos.SellerResponse> sellers() {
        return sellers.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Mapuje encję Seller na DTO odpowiedzi API.
     *
     * DTO zawiera:
     * - sellerId,
     * - ownerId,
     * - e-mail właściciela,
     * - nazwę sprzedawcy,
     * - slug,
     * - status,
     * - prowizję marketplace.
     *
     * Nie zwracamy encji JPA bezpośrednio na zewnątrz.
     */
    public MarketplaceDtos.SellerResponse toResponse(Seller seller) {
        return new MarketplaceDtos.SellerResponse(
                seller.getId(),
                seller.getOwner().getId(),
                seller.getOwner().getEmail(),
                seller.getDisplayName(),
                seller.getSlug(),
                seller.getStatus(),
                seller.getCommissionRate()
        );
    }
}