package com.example.ecommerce.checkout;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.checkout.dto.CheckoutDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller odpowiedzialny za rozpoczęcie procesu checkoutu.
 *
 * Checkout to krytyczny moment w e-commerce:
 * - koszyk jest zamieniany w zamówienie,
 * - rezerwowany jest stock,
 * - tworzona jest płatność,
 * - system przechodzi z operacji "przeglądania" do operacji transakcyjnej.
 *
 * Ta klasa nie zawiera logiki biznesowej.
 * Jej zadaniem jest przyjęcie HTTP requestu, pobranie użytkownika z kontekstu
 * bezpieczeństwa, odczytanie Idempotency-Key i przekazanie wszystkiego do CheckoutService.
 */
@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    /**
     * Serwis checkoutu.
     *
     * To w nim znajduje się właściwa logika:
     * - pobranie koszyka,
     * - walidacja pozycji,
     * - utworzenie zamówienia,
     * - rezerwacja inventory,
     * - utworzenie płatności,
     * - obsługa idempotencji.
     */
    private final CheckoutService checkout;

    /**
     * Constructor injection.
     *
     * Controller wymaga tylko CheckoutService,
     * bo cała logika procesu zakupowego jest delegowana niżej.
     */
    public CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    /**
     * Uruchamia checkout dla aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * POST /api/checkout
     *
     * Kluczowe elementy:
     *
     * @AuthenticationPrincipal AppUser user
     * Użytkownik pochodzi z tokena/autoryzacji, a nie z request body.
     * Dzięki temu klient nie może wykonać checkoutu w imieniu innego usera
     * przez podstawienie cudzego userId.
     *
     * @RequestHeader("Idempotency-Key") String idempotencyKey
     * Klucz idempotencji chroni przed podwójnym checkoutem.
     *
     * To jest krytyczne, bo użytkownik może:
     * - kliknąć "Kupuję" dwa razy,
     * - odświeżyć stronę,
     * - mieć timeout w przeglądarce,
     * - ponowić request z aplikacji mobilnej,
     * - trafić na retry po stronie klienta lub gatewaya.
     *
     * Bez idempotencji mogłyby powstać:
     * - dwa zamówienia,
     * - dwie płatności,
     * - podwójna rezerwacja stocku.
     *
     * @Valid @RequestBody CheckoutDtos.CheckoutRequest request
     * Waliduje dane checkoutu, np. adres dostawy, adres rozliczeniowy
     * i metodę dostawy.
     *
     * Controller zwraca CheckoutResponse, czyli zwykle:
     * - dane utworzonego zamówienia,
     * - dane płatności,
     * - ewentualnie URL/sesję płatności w bardziej rozbudowanej wersji.
     */
    @PostMapping
    public CheckoutDtos.CheckoutResponse checkout(
            @AuthenticationPrincipal AppUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CheckoutDtos.CheckoutRequest request
    ) {
        return checkout.checkout(user, idempotencyKey, request);
    }
}