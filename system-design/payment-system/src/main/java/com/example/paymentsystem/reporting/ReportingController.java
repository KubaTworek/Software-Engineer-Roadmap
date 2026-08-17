package com.example.paymentsystem.reporting;

import org.springframework.web.bind.annotation.*;

/**
 * REST controller odpowiedzialny za raportowanie płatności.
 *
 * W kontekście Payment Systemu raportowanie służy do szybkiego podglądu
 * kondycji biznesu i działania platformy.
 *
 * Ten controller udostępnia dane zagregowane, a nie pojedyncze płatności.
 * Przykładowo:
 * - liczba wszystkich płatności,
 * - liczba udanych płatności,
 * - liczba nieudanych płatności,
 * - łączny wolumen,
 * - przychód platformy z prowizji,
 * - wartość netto dla merchantów,
 * - rozkład płatności między PSP.
 *
 * Controller nie liczy danych samodzielnie.
 * Deleguje logikę agregacji do ReportingService.
 */
@RestController
@RequestMapping("/v1/reports")
public class ReportingController {

    /**
     * Serwis raportowy.
     *
     * Odpowiada za pobranie danych z repozytoriów
     * i przeliczenie ich na gotowy model raportowy.
     */
    private final ReportingService service;

    public ReportingController(ReportingService service) {
        this.service = service;
    }

    /**
     * Zwraca zbiorczy raport całej platformy płatniczej.
     *
     * Endpoint przydatny dla:
     * - panelu admina,
     * - monitoringu biznesowego,
     * - analizy wolumenu płatności,
     * - sprawdzania udziału poszczególnych PSP,
     * - kontroli przychodów platformy.
     *
     * Przykładowe informacje w odpowiedzi:
     * - totalPayments,
     * - succeededPayments,
     * - failedPayments,
     * - totalVolume,
     * - platformFees,
     * - merchantNet,
     * - stripeMockPayments,
     * - adyenMockPayments,
     * - payuMockPayments.
     *
     * @return zagregowany raport płatności
     */
    @GetMapping("/summary")
    public ReportResponse summary() {
        return service.summary();
    }
}