package com.example.ecommerce.invoice;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.invoice.dto.InvoiceDtos;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller odpowiedzialny za operacje na fakturach klienta.
 *
 * W aplikacji e-commerce faktura jest dokumentem powiązanym z zamówieniem.
 * Ten controller udostępnia API do:
 * - wystawienia faktury dla zamówienia,
 * - pobrania dokumentu faktury.
 *
 * Controller nie zawiera logiki księgowej ani generowania dokumentu.
 * Całość deleguje do InvoiceService.
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    /**
     * Serwis faktur.
     *
     * Odpowiada za właściwą logikę:
     * - sprawdzenie, czy zamówienie należy do użytkownika,
     * - sprawdzenie, czy faktura już istnieje,
     * - wyliczenie kwot netto/VAT/brutto,
     * - wygenerowanie numeru faktury,
     * - zapis dokumentu,
     * - publikację eventu InvoiceIssued.
     */
    private final InvoiceService invoices;

    /**
     * Constructor injection.
     *
     * Controller potrzebuje tylko InvoiceService,
     * bo nie powinien samodzielnie dotykać zamówień ani repozytorium faktur.
     */
    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    /**
     * Wystawia fakturę dla konkretnego zamówienia.
     *
     * Endpoint:
     * POST /api/invoices/orders/{orderId}
     *
     * @AuthenticationPrincipal AppUser user:
     * użytkownik jest pobierany z kontekstu bezpieczeństwa.
     * Nie przyjmujemy userId z requestu, żeby klient nie mógł wystawić
     * faktury dla cudzego zamówienia.
     *
     * @PathVariable Long orderId:
     * identyfikator zamówienia, dla którego ma zostać wystawiona faktura.
     *
     * Kluczowe:
     * InvoiceService musi sprawdzić, czy orderId należy do aktualnego użytkownika.
     * Sam fakt, że klient zna orderId, nie może dawać dostępu do faktury.
     */
    @PostMapping("/orders/{orderId}")
    public InvoiceDtos.InvoiceResponse issue(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long orderId
    ) {
        return invoices.issue(user, orderId);
    }

    /**
     * Pobiera dokument faktury dla zamówienia.
     *
     * Endpoint:
     * GET /api/invoices/orders/{orderId}/document
     *
     * W tej wersji projektu dokument jest zwracany jako DTO,
     * np. z numerem faktury i treścią HTML.
     *
     * Produkcyjnie ten endpoint mógłby:
     * - zwracać PDF,
     * - generować podpisany URL do pliku,
     * - pobierać dokument z object storage,
     * - wymagać dodatkowej autoryzacji dla panelu admina.
     *
     * Tak jak przy wystawianiu faktury:
     * InvoiceService musi potwierdzić, że zamówienie należy do aktualnego usera.
     */
    @GetMapping("/orders/{orderId}/document")
    public InvoiceDtos.InvoiceDocumentResponse document(
            @AuthenticationPrincipal AppUser user,
            @PathVariable Long orderId
    ) {
        return invoices.document(user, orderId);
    }
}