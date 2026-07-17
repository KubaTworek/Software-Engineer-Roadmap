package com.example.ecommerce.returns;

import com.example.ecommerce.auth.AppUser;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.order.CustomerOrder;
import com.example.ecommerce.order.OrderService;
import com.example.ecommerce.outbox.OutboxService;
import com.example.ecommerce.returns.dto.ReturnDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Serwis domenowy odpowiedzialny za obsługę zwrotów.
 *
 * Zwrot jest procesem posprzedażowym.
 * Klient zgłasza zwrot pozycji z zamówienia, a system tworzy ReturnRequest,
 * który później może zostać zaakceptowany, odrzucony, oznaczony jako otrzymany
 * albo zrefundowany.
 *
 * Ten serwis odpowiada za:
 * - utworzenie zgłoszenia zwrotu,
 * - sprawdzenie, czy zamówienie należy do użytkownika,
 * - zapis pozycji zwrotu,
 * - wyliczenie żądanej kwoty zwrotu,
 * - zatwierdzenie zwrotu,
 * - oznaczenie zwrotu jako zrefundowanego,
 * - listę zwrotów użytkownika,
 * - publikację eventów przez outbox.
 */
@Service
public class ReturnService {

    /**
     * Repozytorium zgłoszeń zwrotów.
     *
     * Przechowuje ReturnRequest oraz powiązane ReturnRequestItem.
     */
    private final ReturnRequestRepository returns;

    /**
     * Serwis zamówień.
     *
     * Używany do kontroli dostępu.
     *
     * Nie pobieramy zamówienia samym orderId.
     * Pobieramy je przez OrderService, który sprawdza orderId + userId.
     *
     * Dzięki temu klient nie może zgłosić zwrotu dla cudzego zamówienia.
     */
    private final OrderService orders;

    /**
     * Serwis outbox.
     *
     * Po ważnych zmianach w procesie zwrotu zapisujemy eventy:
     * - ReturnRequested,
     * - ReturnApproved,
     * - ReturnRefunded.
     *
     * Downstream procesy mogą na nie reagować, np. ERP, WMS, payment/refund provider,
     * notification-service albo customer support.
     */
    private final OutboxService outbox;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje repozytorium zwrotów, serwisu zamówień i outboxa.
     */
    public ReturnService(
            ReturnRequestRepository returns,
            OrderService orders,
            OutboxService outbox
    ) {
        this.returns = returns;
        this.orders = orders;
        this.outbox = outbox;
    }

    /**
     * Tworzy nowe zgłoszenie zwrotu.
     *
     * Flow:
     * 1. Pobierz zamówienie użytkownika przez OrderService.
     * 2. Utwórz ReturnRequest z powodem zwrotu.
     * 3. Sprawdź, czy request zawiera co najmniej jedną pozycję.
     * 4. Dodaj pozycje zwrotu.
     * 5. Zapisz zgłoszenie zwrotu.
     * 6. Zapisz event ReturnRequested do outboxa.
     * 7. Zwróć DTO odpowiedzi.
     *
     * @Transactional:
     * zgłoszenie zwrotu, jego pozycje i event outbox zapisują się atomowo.
     */
    @Transactional
    public ReturnDtos.ReturnResponse create(
            AppUser user,
            ReturnDtos.CreateReturnRequest request
    ) {
        /*
         * Pobranie zamówienia z kontrolą właściciela.
         *
         * Jeśli orderId nie istnieje albo nie należy do usera,
         * OrderService zwróci 404.
         *
         * To chroni przed zgłaszaniem zwrotów dla cudzych zamówień.
         */
        CustomerOrder order = orders.getOrderEntityForUser(user, request.orderId());

        /*
         * Tworzymy główny obiekt zgłoszenia zwrotu.
         *
         * Domyślny status powinien być REQUESTED.
         */
        ReturnRequest returnRequest = new ReturnRequest(
                order,
                user,
                request.reason()
        );

        /*
         * Zwrot bez pozycji nie ma sensu biznesowo.
         *
         * Klient musi wskazać, co chce zwrócić.
         */
        if (request.items() == null || request.items().isEmpty()) {
            throw ApiException.badRequest("Return request requires at least one item");
        }

        /*
         * Dodajemy pozycje zwrotu.
         *
         * Każda pozycja zawiera:
         * - orderItemId,
         * - quantity,
         * - refundAmount.
         *
         * Uwaga produkcyjna:
         * warto dodatkowo sprawdzić, czy orderItemId faktycznie należy do tego ordera
         * oraz czy quantity nie przekracza ilości zakupionej.
         */
        for (var item : request.items()) {
            returnRequest.addItem(
                    new ReturnRequestItem(
                            item.orderItemId(),
                            item.quantity(),
                            item.refundAmount()
                    )
            );
        }

        /*
         * Zapis zgłoszenia zwrotu.
         *
         * Encja ReturnRequest powinna mieć cascade dla ReturnRequestItem,
         * żeby pozycje zapisały się razem z głównym zgłoszeniem.
         */
        ReturnRequest saved = returns.save(returnRequest);

        /*
         * Event ReturnRequested.
         *
         * Może uruchomić:
         * - wiadomość do klienta,
         * - zadanie dla customer support,
         * - synchronizację z ERP,
         * - proces RMA,
         * - rezerwację procesu magazynowego w WMS.
         */
        outbox.saveEvent(
                "ReturnRequest",
                saved.getId().toString(),
                "ReturnRequested",
                Map.of(
                        "returnId", saved.getId(),
                        "orderId", order.getId(),
                        "userId", user.getId()
                )
        );

        return toResponse(saved);
    }

    /**
     * Akceptuje zgłoszenie zwrotu.
     *
     * To operacja adminowa lub operacja customer support.
     *
     * Flow:
     * 1. Pobierz ReturnRequest.
     * 2. Jeśli nie istnieje, zwróć 404.
     * 3. Zmień status na APPROVED.
     * 4. Zapisz event ReturnApproved.
     * 5. Zwróć aktualny stan zwrotu.
     *
     * Uwaga:
     * W tej metodzie nie ma kontroli roli admina.
     * Powinna być wymuszona na poziomie AdminController albo Spring Security.
     */
    @Transactional
    public ReturnDtos.ReturnResponse approve(Long returnId) {
        ReturnRequest request = returns.findById(returnId)
                .orElseThrow(() -> ApiException.notFound("Return not found"));

        /*
         * Zmiana statusu jest zamknięta w encji ReturnRequest.
         *
         * W produkcji warto pilnować przejść statusów, np.
         * REQUESTED -> APPROVED, ale nie REFUNDED -> APPROVED.
         */
        request.approve();

        /*
         * Event ReturnApproved.
         *
         * Może uruchomić np. e-mail do klienta albo instrukcję odesłania produktu.
         */
        outbox.saveEvent(
                "ReturnRequest",
                request.getId().toString(),
                "ReturnApproved",
                Map.of("returnId", request.getId())
        );

        return toResponse(request);
    }

    /**
     * Oznacza zwrot jako zrefundowany.
     *
     * To zwykle powinno nastąpić po wykonaniu refundu przez payment providera
     * albo po potwierdzeniu księgowym.
     *
     * Flow:
     * 1. Pobierz ReturnRequest.
     * 2. Zmień status na REFUNDED.
     * 3. Zapisz event ReturnRefunded.
     * 4. Zwróć aktualny stan zwrotu.
     *
     * Uwaga:
     * Ta metoda nie wykonuje realnego refundu pieniędzy.
     * Tylko oznacza proces jako zrefundowany.
     */
    @Transactional
    public ReturnDtos.ReturnResponse markRefunded(Long returnId) {
        ReturnRequest request = returns.findById(returnId)
                .orElseThrow(() -> ApiException.notFound("Return not found"));

        /*
         * Zmieniamy status na REFUNDED.
         *
         * Produkcyjnie warto sprawdzić, czy wcześniejszy status pozwala na refund,
         * np. APPROVED albo RECEIVED.
         */
        request.markRefunded();

        /*
         * Event ReturnRefunded.
         *
         * Może zostać użyty do:
         * - synchronizacji z ERP,
         * - wysłania maila do klienta,
         * - analityki zwrotów,
         * - korekty faktury,
         * - aktualizacji raportów finansowych.
         */
        outbox.saveEvent(
                "ReturnRequest",
                request.getId().toString(),
                "ReturnRefunded",
                Map.of(
                        "returnId", request.getId(),
                        "orderId", request.getOrder().getId()
                )
        );

        return toResponse(request);
    }

    /**
     * Zwraca listę zwrotów aktualnie zalogowanego użytkownika.
     *
     * Używane przez panel klienta, np. "Moje zwroty".
     *
     * Wynik jest filtrowany po userId i sortowany od najnowszych.
     */
    @Transactional(readOnly = true)
    public List<ReturnDtos.ReturnResponse> myReturns(AppUser user) {
        return returns.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Mapuje encję ReturnRequest na DTO odpowiedzi API.
     *
     * DTO zawiera:
     * - returnId,
     * - orderId,
     * - status zwrotu,
     * - żądaną kwotę refundu,
     * - powód zwrotu,
     * - datę utworzenia.
     *
     * Nie zwracamy encji JPA bezpośrednio do API.
     */
    private ReturnDtos.ReturnResponse toResponse(ReturnRequest request) {
        return new ReturnDtos.ReturnResponse(
                request.getId(),
                request.getOrder().getId(),
                request.getStatus(),
                request.getRequestedRefundAmount(),
                request.getReason(),
                request.getCreatedAt()
        );
    }
}