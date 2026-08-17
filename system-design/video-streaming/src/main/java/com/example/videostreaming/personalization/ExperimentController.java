package com.example.videostreaming.personalization;

import com.example.videostreaming.auth.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Kontroler eksperymentów A/B.
 *
 * Główna odpowiedzialność:
 * - zwraca wariant eksperymentu przypisany do użytkownika,
 * - zapewnia stabilne przypisanie userId -> variant,
 * - pozwala frontendowi zdecydować, który wariant UI/logiki pokazać.
 *
 * Przykład użycia:
 * - test różnych algorytmów rekomendacji na stronie głównej,
 * - test kolejności sekcji homepage,
 * - test innego rankingu trending,
 * - test nowego playera albo layoutu.
 *
 * Ważne:
 * Ten kontroler tylko wystawia API.
 * Logika przypisania, bucketingu i zapisu assignmentu znajduje się w ExperimentService.
 */
@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    /**
     * Serwis eksperymentów.
     *
     * Odpowiada za:
     * - znalezienie konfiguracji eksperymentu,
     * - przypisanie użytkownika do wariantu,
     * - utrzymanie stabilnego assignmentu,
     * - informację, czy assignment został utworzony teraz, czy istniał wcześniej.
     */
    private final ExperimentService experiments;

    public ExperimentController(ExperimentService experiments) {
        this.experiments = experiments;
    }

    /**
     * Zwraca przypisanie aktualnego użytkownika do wariantu eksperymentu.
     *
     * Endpoint używany przez klienta, gdy aplikacja potrzebuje wiedzieć,
     * który wariant funkcji ma pokazać użytkownikowi.
     *
     * Flow:
     * 1. Klient podaje experimentKey, np. home_recommendations_ranking.
     * 2. Backend bierze userId z AuthenticationPrincipal.
     * 3. ExperimentService zwraca istniejący assignment albo tworzy nowy.
     * 4. API zwraca experimentKey, variantKey i flagę newlyAssigned.
     *
     * userId nie pochodzi z requestu.
     * Dzięki temu użytkownik nie może poprosić o wariant dla innego konta.
     *
     * @param user aktualnie zalogowany użytkownik
     * @param experimentKey techniczny klucz eksperymentu
     * @return wariant przypisany użytkownikowi
     */
    @GetMapping("/{experimentKey}/assignment")
    public AssignmentResponse assignment(@AuthenticationPrincipal User user,
                                         @PathVariable String experimentKey) {
        var assignment = experiments.assignment(experimentKey, user.getId());

        return new AssignmentResponse(
                assignment.experimentKey(),
                assignment.variantKey(),
                assignment.newlyAssigned()
        );
    }
}