package com.example.ecommerce.invoice;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.order.OrderService;
import com.example.ecommerce.outbox.OutboxService;
import com.example.ecommerce.invoice.dto.InvoiceDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;

/**
 * Serwis domenowy odpowiedzialny za faktury.
 *
 * W aplikacji e-commerce faktura jest dokumentem powiązanym z zamówieniem.
 *
 * Ten serwis odpowiada za:
 * - sprawdzenie, czy faktura dla zamówienia już istnieje,
 * - sprawdzenie, czy zamówienie należy do aktualnego użytkownika,
 * - wyliczenie netto/VAT/brutto,
 * - wygenerowanie numeru faktury,
 * - zapis dokumentu faktury,
 * - publikację eventu InvoiceIssued do outboxa,
 * - pobranie dokumentu faktury.
 *
 * Controller tylko przyjmuje request HTTP.
 * Ta klasa zawiera właściwą logikę biznesową fakturowania.
 */
@Service
public class InvoiceService {

    /**
     * Repozytorium faktur.
     *
     * Używane do:
     * - sprawdzenia, czy faktura dla orderId już istnieje,
     * - zapisania nowej faktury,
     * - pobrania dokumentu faktury po orderId.
     */
    private final InvoiceRepository invoices;

    /**
     * Serwis zamówień.
     *
     * Bardzo ważna zależność bezpieczeństwa.
     *
     * Nie pobieramy zamówienia bezpośrednio po orderId z repozytorium,
     * tylko przez OrderService, ponieważ on sprawdza, czy zamówienie
     * należy do aktualnie zalogowanego użytkownika.
     */
    private final OrderService orders;

    /**
     * Serwis outbox.
     *
     * Po wystawieniu faktury zapisujemy event InvoiceIssued.
     *
     * Dzięki temu inne procesy mogą zareagować asynchronicznie, np.:
     * - wysłać fakturę e-mailem,
     * - zsynchronizować dokument z ERP,
     * - zapisać zdarzenie audytowe,
     * - uruchomić raportowanie księgowe.
     */
    private final OutboxService outbox;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje repozytorium faktur, serwisu zamówień
     * oraz outboxa do publikowania zdarzeń domenowych.
     */
    public InvoiceService(
            InvoiceRepository invoices,
            OrderService orders,
            OutboxService outbox
    ) {
        this.invoices = invoices;
        this.orders = orders;
        this.outbox = outbox;
    }

    /**
     * Wystawia fakturę dla zamówienia użytkownika.
     *
     * Flow:
     * 1. Sprawdź, czy faktura dla orderId już istnieje.
     * 2. Pobierz zamówienie i sprawdź, czy należy do usera.
     * 3. Weź kwotę brutto z zamówienia.
     * 4. Wylicz netto i VAT.
     * 5. Wygeneruj numer faktury.
     * 6. Wygeneruj dokument HTML.
     * 7. Zapisz fakturę.
     * 8. Zapisz event InvoiceIssued w outbox.
     * 9. Zwróć DTO odpowiedzi.
     *
     * @Transactional:
     * zapis faktury i eventu outbox dzieją się w jednej transakcji.
     * Dzięki temu nie będzie sytuacji, że faktura powstała, ale event nie.
     */
    @Transactional
    public InvoiceDtos.InvoiceResponse issue(AppUser user, Long orderId) {
        /*
         * Jedno zamówienie powinno mieć maksymalnie jedną fakturę pierwotną.
         *
         * Jeśli faktura już istnieje, zwracamy konflikt biznesowy.
         * To chroni przed duplikatami faktur dla tego samego zamówienia.
         */
        invoices.findByOrderId(orderId).ifPresent(existing -> {
            throw ApiException.conflict("Invoice already exists for this order");
        });

        /*
         * Pobieramy zamówienie przez OrderService.
         *
         * Kluczowe:
         * OrderService powinien sprawdzić, czy orderId należy do aktualnego usera.
         * Dzięki temu użytkownik nie wystawi faktury dla cudzego zamówienia.
         */
        CustomerOrder order = orders.getOrderEntityForUser(user, orderId);

        /*
         * Kwota brutto pochodzi z zamówienia.
         *
         * Faktura nie przelicza koszyka od nowa.
         * Bazuje na snapshotach i totalu zapisanym w CustomerOrder.
         */
        BigDecimal gross = order.getTotalAmount();

        /*
         * Uproszczone wyliczenie VAT dla stawki 23%.
         *
         * brutto = netto * 1.23
         * netto = brutto / 1.23
         * VAT = brutto - netto
         *
         * W systemie produkcyjnym stawka VAT powinna zależeć od:
         * - kraju,
         * - typu produktu,
         * - statusu B2B/B2C,
         * - danych podatkowych klienta,
         * - reguł OSS/IOSS/VAT UE.
         */
        BigDecimal net = gross.divide(
                BigDecimal.valueOf(1.23),
                2,
                RoundingMode.HALF_UP
        );

        BigDecimal vat = gross.subtract(net);

        /*
         * Prosty numer faktury dla MVP.
         *
         * Format:
         * FV/{rok}/{orderId}
         *
         * W produkcji numeracja faktur powinna być osobną, transakcyjną sekwencją,
         * odporną na równoległe wystawianie dokumentów i zgodną z wymaganiami księgowymi.
         */
        String number = "FV/" + LocalDate.now().getYear() + "/" + order.getId();

        /*
         * MVP dokumentu faktury jako HTML.
         *
         * W tej wersji przechowujemy prosty dokument HTML bez generowania PDF.
         * Później można podmienić ten fragment na:
         * - template engine,
         * - generator PDF,
         * - zapis pliku do object storage,
         * - podpisany URL do pobrania dokumentu.
         */
        String html =
                "<html><body>"
                        + "<h1>Invoice " + number + "</h1>"
                        + "<p>Order: " + order.getOrderNumber() + "</p>"
                        + "<p>Total: " + gross + " " + order.getCurrency() + "</p>"
                        + "</body></html>";

        /*
         * Zapis faktury w bazie.
         *
         * Faktura przechowuje:
         * - powiązane zamówienie,
         * - numer faktury,
         * - kwoty netto/VAT/brutto,
         * - walutę,
         * - dokument HTML.
         */
        Invoice invoice = invoices.save(
                new Invoice(
                        order,
                        number,
                        net,
                        vat,
                        gross,
                        order.getCurrency(),
                        html
                )
        );

        /*
         * Event domenowy po wystawieniu faktury.
         *
         * Dzięki Outbox Pattern downstream services mogą niezawodnie
         * przetworzyć zdarzenie po commitcie transakcji.
         */
        outbox.saveEvent(
                "Invoice",
                invoice.getId().toString(),
                "InvoiceIssued",
                Map.of(
                        "invoiceId", invoice.getId(),
                        "orderId", orderId,
                        "invoiceNumber", number
                )
        );

        return toResponse(invoice);
    }

    /**
     * Pobiera dokument faktury dla zamówienia użytkownika.
     *
     * Flow:
     * 1. Sprawdź, czy zamówienie należy do usera.
     * 2. Pobierz fakturę po orderId.
     * 3. Jeśli faktura nie istnieje, zwróć 404.
     * 4. Zwróć numer faktury i dokument HTML.
     *
     * @Transactional(readOnly = true):
     * operacja tylko odczytuje dane, więc może korzystać z read-replica.
     */
    @Transactional(readOnly = true)
    public InvoiceDtos.InvoiceDocumentResponse document(AppUser user, Long orderId) {
        /*
         * To wywołanie służy głównie do kontroli dostępu.
         *
         * Nie interesuje nas tu wynik, tylko fakt, że OrderService
         * zweryfikuje własność zamówienia.
         */
        orders.getOrderEntityForUser(user, orderId);

        /*
         * Dopiero po potwierdzeniu dostępu pobieramy fakturę.
         *
         * Dzięki temu użytkownik nie może pobrać dokumentu faktury
         * dla cudzego zamówienia tylko przez odgadnięcie orderId.
         */
        Invoice invoice = invoices.findByOrderId(orderId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));

        return new InvoiceDtos.InvoiceDocumentResponse(
                invoice.getInvoiceNumber(),
                invoice.getHtmlDocument()
        );
    }

    /**
     * Mapuje encję Invoice na DTO odpowiedzi API.
     *
     * Nie zwracamy encji JPA bezpośrednio na zewnątrz.
     * DTO zawiera tylko dane potrzebne klientowi API:
     * - invoiceId,
     * - orderId,
     * - numer faktury,
     * - kwoty netto/VAT/brutto,
     * - walutę,
     * - datę wystawienia.
     */
    private InvoiceDtos.InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceDtos.InvoiceResponse(
                invoice.getId(),
                invoice.getOrder().getId(),
                invoice.getInvoiceNumber(),
                invoice.getNetAmount(),
                invoice.getVatAmount(),
                invoice.getGrossAmount(),
                invoice.getCurrency(),
                invoice.getIssuedAt()
        );
    }
}