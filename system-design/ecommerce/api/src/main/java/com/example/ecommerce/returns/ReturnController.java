package com.example.ecommerce.returns;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.returns.dto.ReturnDtos;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller odpowiedzialny za API zwrotów aktualnie zalogowanego użytkownika.
 *
 * Zwrot w e-commerce jest procesem po sprzedaży.
 * Klient może zgłosić chęć zwrotu produktów z konkretnego zamówienia,
 * a system tworzy ReturnRequest, który później może być obsłużony przez admina,
 * magazyn albo dział obsługi klienta.
 *
 * Controller nie zawiera logiki biznesowej zwrotów.
 * Deleguje ją do ReturnService.
 */
@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    /**
     * Serwis zwrotów.
     *
     * Odpowiada za właściwą logikę:
     * - sprawdzenie, czy zamówienie należy do użytkownika,
     * - walidację pozycji zwrotu,
     * - utworzenie ReturnRequest,
     * - zapis pozycji zwrotu,
     * - pobranie zwrotów użytkownika,
     * - publikację eventów przez outbox.
     */
    private final ReturnService returns;

    /**
     * Constructor injection.
     *
     * Controller potrzebuje tylko ReturnService.
     * Nie powinien bezpośrednio korzystać z repozytoriów zwrotów ani zamówień,
     * bo kontrola dostępu i workflow statusów należą do serwisu.
     */
    public ReturnController(ReturnService returns) {
        this.returns = returns;
    }

    /**
     * Tworzy zgłoszenie zwrotu dla aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * POST /api/returns
     *
     * @AuthenticationPrincipal AppUser user:
     * użytkownik pochodzi z kontekstu bezpieczeństwa.
     * Nie przyjmujemy userId w request body, żeby klient nie mógł zgłosić
     * zwrotu w imieniu innej osoby.
     *
     * @Valid @RequestBody:
     * waliduje request, np. czy orderId, reason i pozycje zwrotu są poprawne.
     *
     * Kluczowe:
     * ReturnService musi sprawdzić, czy orderId należy do tego użytkownika.
     * Sam fakt, że klient zna orderId, nie może pozwalać na zwrot cudzego zamówienia.
     */
    @PostMapping
    public ReturnDtos.ReturnResponse create(
            @AuthenticationPrincipal AppUser user,
            @Valid @RequestBody ReturnDtos.CreateReturnRequest request
    ) {
        return returns.create(user, request);
    }

    /**
     * Zwraca listę zwrotów aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/returns
     *
     * Używane np. w panelu klienta:
     * - historia zwrotów,
     * - status zgłoszenia,
     * - kwota oczekiwanego zwrotu,
     * - powód zwrotu.
     *
     * Tak jak przy zamówieniach, użytkownik nie przekazuje userId.
     * Lista jest filtrowana po użytkowniku z tokena.
     */
    @GetMapping
    public List<ReturnDtos.ReturnResponse> myReturns(
            @AuthenticationPrincipal AppUser user
    ) {
        return returns.myReturns(user);
    }
}