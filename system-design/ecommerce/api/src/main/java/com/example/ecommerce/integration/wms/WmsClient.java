package com.example.ecommerce.integration.wms;

import com.example.ecommerce.integration.IntegrationRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Klient integracji z WMS.
 *
 * WMS, czyli Warehouse Management System, odpowiada za operacje magazynowe:
 * - rezerwację towaru pod zamówienie,
 * - zwolnienie rezerwacji,
 * - kompletację,
 * - pakowanie,
 * - wysyłkę,
 * - aktualizację stanów magazynowych.
 *
 * W tej wersji projektu klient jest mockiem.
 * Nie wysyła jeszcze realnych requestów HTTP, tylko loguje operacje.
 *
 * Ważne:
 * Klasa jest już przygotowana pod prawdziwą integrację, bo wywołania są opakowane
 * w IntegrationRetryService.
 */
@Component
public class WmsClient {

    /**
     * Logger techniczny.
     *
     * W MVP zastępuje realne wywołanie WMS.
     * Dzięki logom widać, jaki payload zostałby wysłany do systemu magazynowego.
     */
    private static final Logger log = LoggerFactory.getLogger(WmsClient.class);

    /**
     * Wspólny serwis retry dla integracji zewnętrznych.
     *
     * WMS jest systemem zewnętrznym, więc może mieć:
     * - timeouty,
     * - chwilowe błędy sieci,
     * - niedostępność,
     * - odpowiedzi 5xx.
     *
     * Retry chroni aplikację przed jednorazowymi problemami technicznymi.
     */
    private final IntegrationRetryService retry;

    /**
     * Constructor injection.
     *
     * Klient WMS potrzebuje tylko mechanizmu retry.
     * W produkcyjnej wersji doszłyby np. RestClient/WebClient,
     * baseUrl, credentials i mapper payloadów.
     */
    public WmsClient(IntegrationRetryService retry) {
        this.retry = retry;
    }

    /**
     * Wysyła do WMS informację o rezerwacji towaru pod zamówienie.
     *
     * Parametry:
     * - orderId — zamówienie, dla którego rezerwujemy stock,
     * - variantId — konkretny wariant produktu,
     * - quantity — liczba sztuk do zarezerwowania.
     *
     * W procesie e-commerce ta operacja powinna zostać wykonana po utworzeniu
     * zamówienia albo podczas checkoutu, kiedy system blokuje dostępny stock.
     *
     * W tej wersji:
     * - nie ma jeszcze realnego HTTP calla,
     * - operacja jest logowana,
     * - retry już działa wokół tej operacji.
     *
     * Produkcyjnie tutaj powstałby request do WMS, np.:
     * POST /reservations
     */
    public void sendReservation(Long orderId, Long variantId, int quantity) {
        retry.run(
                "wms.reserve",
                () -> log.info(
                        "MOCK_WMS_RESERVATION orderId={}, variantId={}, quantity={}",
                        orderId,
                        variantId,
                        quantity
                )
        );
    }

    /**
     * Wysyła do WMS informację o zwolnieniu rezerwacji dla zamówienia.
     *
     * Typowe przypadki:
     * - płatność się nie udała,
     * - zamówienie zostało anulowane,
     * - rezerwacja wygasła,
     * - klient porzucił checkout,
     * - system inventory expiration worker zwolnił stock.
     *
     * Parametr:
     * - orderId — zamówienie, którego rezerwacje mają zostać zwolnione.
     *
     * Produkcyjnie tutaj powstałby request do WMS, np.:
     * POST /reservations/{orderId}/release
     *
     * W MVP logujemy operację, ale zostawiamy retry i nazwę integracji,
     * żeby później łatwo podmienić logowanie na realny klient HTTP.
     */
    public void sendRelease(Long orderId) {
        retry.run(
                "wms.release",
                () -> log.info(
                        "MOCK_WMS_RELEASE orderId={}",
                        orderId
                )
        );
    }
}