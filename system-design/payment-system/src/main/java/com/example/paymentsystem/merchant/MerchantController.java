package com.example.paymentsystem.merchant;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller do zarządzania merchantami.
 *
 * Merchant to sprzedawca / podmiot, który przyjmuje płatności
 * przez naszą platformę.
 *
 * W kontekście całego Payment Systemu merchant jest potrzebny do:
 * - przypisania płatności do konkretnego sprzedawcy,
 * - określenia waluty settlementu,
 * - naliczania splitu marketplace,
 * - księgowania salda merchanta w ledgerze,
 * - tworzenia payoutów.
 *
 * To jest prosty controller administracyjny.
 * W produkcyjnym systemie byłby częścią panelu onboardingowego/KYB.
 */
@RestController
@RequestMapping("/v1/merchants")
public class MerchantController {

    /**
     * Repozytorium merchantów.
     *
     * W tej wersji controller korzysta bezpośrednio z repository,
     * bo operacje są proste:
     * - utworzenie merchanta,
     * - pobranie listy merchantów.
     *
     * Przy bardziej złożonym onboardingu warto dodać MerchantService,
     * np. do obsługi KYB, statusów aktywacji, limitów i konfiguracji payoutów.
     */
    private final MerchantRepository repository;

    public MerchantController(MerchantRepository repository) {
        this.repository = repository;
    }

    /**
     * Tworzy nowego merchanta.
     *
     * Merchant reprezentuje sprzedawcę, dla którego później będą tworzone płatności.
     *
     * Kluczowe dane:
     * - name: nazwa sprzedawcy,
     * - settlementCurrency: waluta, w której merchant chce się rozliczać.
     *
     * Przykład:
     * klient może zapłacić w EUR,
     * ale merchant może mieć settlementCurrency = PLN.
     *
     * Wtedy FxService przeliczy kwotę płatności na walutę settlementu merchanta.
     *
     * @param request dane potrzebne do utworzenia merchanta
     * @return zapisany merchant
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Merchant create(@Valid @RequestBody CreateMerchantRequest request) {
        return repository.save(new Merchant(
                request.name(),
                request.settlementCurrency()
        ));
    }

    /**
     * Zwraca listę wszystkich merchantów.
     *
     * Endpoint pomocniczy do testowania i podglądu konfiguracji platformy.
     *
     * W realnym systemie ta lista powinna mieć:
     * - paginację,
     * - filtrowanie,
     * - autoryzację admina,
     * - ograniczenie dostępu per organizacja/tenant.
     *
     * @return lista merchantów zapisanych w systemie
     */
    @GetMapping
    public List<Merchant> list() {
        return repository.findAll();
    }
}