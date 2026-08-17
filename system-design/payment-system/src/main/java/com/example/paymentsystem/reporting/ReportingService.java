package com.example.paymentsystem.reporting;

import com.example.paymentsystem.payment.*;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za budowanie raportu biznesowego platformy płatniczej.
 *
 * ReportingService agreguje dane z płatności i zwraca gotowe podsumowanie:
 * - ile płatności przeszło przez system,
 * - ile zakończyło się sukcesem,
 * - ile zakończyło się błędem,
 * - jaki był łączny wolumen pobranych środków,
 * - ile zarobiła platforma na prowizjach,
 * - jaka kwota netto przypada merchantom,
 * - jak płatności rozkładają się między providerów PSP.
 *
 * To jest warstwa odczytowa.
 * Nie zmienia płatności, nie księguje operacji i nie wykonuje żadnych akcji finansowych.
 */
@Service
public class ReportingService {

    /**
     * Repozytorium płatności.
     *
     * Źródłem danych raportowych są tutaj rekordy Payment.
     *
     * W tej wersji raport bazuje na tabeli payments.
     * W większym systemie część danych raportowych lepiej liczyć z ledgera
     * albo z osobnej tabeli/materialized view przygotowanej pod analytics.
     */
    private final PaymentRepository repository;

    public ReportingService(PaymentRepository repository) {
        this.repository = repository;
    }

    /**
     * Buduje zbiorczy raport płatności dla całej platformy.
     *
     * Raport pokazuje najważniejsze metryki biznesowe:
     * - całkowitą liczbę płatności,
     * - liczbę płatności udanych,
     * - liczbę płatności nieudanych,
     * - captured volume,
     * - przychód platformy z prowizji,
     * - kwotę netto dla merchantów,
     * - liczbę płatności obsłużonych przez każdego PSP.
     *
     * @return zagregowane podsumowanie platformy
     */
    public ReportResponse summary() {

        /**
         * Pobieramy wszystkie płatności.
         *
         * W tej edukacyjnej wersji jest to wystarczające.
         * W produkcji nie powinno się robić findAll() dla dużej tabeli payments.
         *
         * Lepsze rozwiązania produkcyjne:
         * - agregacje SQL,
         * - osobne read model tables,
         * - materialized views,
         * - hurtownia danych,
         * - event-driven analytics pipeline.
         */
        var payments = repository.findAll();

        /**
         * Liczymy całkowity wolumen pobranych środków.
         *
         * Używamy capturedAmount, a nie amount.
         *
         * Dlaczego?
         * - amount oznacza kwotę, którą klient miał zapłacić,
         * - capturedAmount oznacza faktycznie pobraną kwotę.
         *
         * Przy płatnościach manual capture może być mniejszy niż amount.
         */
        long totalVolume = payments.stream()
                .mapToLong(Payment::getCapturedAmount)
                .sum();

        /**
         * Liczymy przychód platformy z prowizji.
         *
         * platformFeeAmount pochodzi ze splitu marketplace.
         *
         * Przykład:
         * payment amount = 10000
         * platform fee = 1000
         * merchant amount = 9000
         */
        long fees = payments.stream()
                .mapToLong(Payment::getPlatformFeeAmount)
                .sum();

        /**
         * Liczymy kwotę netto należną merchantom.
         *
         * merchantAmount to część płatności po odjęciu prowizji platformy.
         *
         * Ta metryka pokazuje, ile pieniędzy powinno finalnie trafić
         * do sprzedawców przed refundami, chargebackami i payoutami.
         */
        long merchantNet = payments.stream()
                .mapToLong(Payment::getMerchantAmount)
                .sum();

        /**
         * Składamy odpowiedź raportową.
         *
         * Count po statusach pokazuje kondycję płatności.
         * Count po providerach pokazuje rozkład ruchu między PSP.
         *
         * To pozwala szybko sprawdzić:
         * - czy płatności działają,
         * - który PSP obsługuje największy ruch,
         * - jaki jest podstawowy wolumen biznesu,
         * - ile platforma zarabia na prowizjach.
         */
        return new ReportResponse(
                payments.size(),

                /**
                 * Za udane traktujemy:
                 * - SUCCEEDED: automatycznie pobrana płatność,
                 * - CAPTURED: manualnie autoryzowana i później pobrana płatność.
                 */
                repository.countByStatus(PaymentStatus.SUCCEEDED)
                        + repository.countByStatus(PaymentStatus.CAPTURED),

                /**
                 * Liczba płatności zakończonych błędem.
                 */
                repository.countByStatus(PaymentStatus.FAILED),

                totalVolume,
                fees,
                merchantNet,

                /**
                 * Liczba płatności obsłużonych przez poszczególnych providerów.
                 *
                 * To jest szczególnie ważne w Stage 6,
                 * bo system ma routing między wieloma PSP.
                 */
                repository.countByProvider(PaymentProvider.STRIPE_MOCK),
                repository.countByProvider(PaymentProvider.ADYEN_MOCK),
                repository.countByProvider(PaymentProvider.PAYU_MOCK)
        );
    }
}