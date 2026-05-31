package pl.jakubtworek.booking.service.async;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationResponse;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.exception.BusinessRuleException;
import pl.jakubtworek.booking.exception.NotFoundException;
import pl.jakubtworek.booking.repository.ReservationRepository;

import java.util.UUID;

/**
 * Serwis odpowiedzialny za transakcyjne zmiany statusu rezerwacji
 * w asynchronicznym flow płatności.
 *
 * Ta klasa jest celowo oddzielona od AsyncReservationService.
 *
 * AsyncReservationService orkiestruje:
 * - payment provider,
 * - timeout,
 * - fallback,
 * - side-effecty,
 * - CompletableFuture.
 *
 * ReservationStatusService odpowiada za:
 * - pobranie rezerwacji,
 * - zmianę statusu encji,
 * - zapis przez dirty checking,
 * - mapowanie wyniku do DTO.
 *
 * Dzięki temu logika transakcyjna jest w zwykłym serwisie Springa i wywołanie
 * z AsyncReservationService przechodzi przez proxy Springa.
 */
@Service
public class ReservationStatusService {

    /**
     * Repozytorium rezerwacji.
     *
     * Używamy metod pobierających rezerwację razem z relacjami potrzebnymi
     * do zbudowania ReservationResponse.
     */
    private final ReservationRepository reservationRepository;

    /**
     * Constructor injection.
     */
    public ReservationStatusService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    /**
     * Potwierdza rezerwację po zaakceptowanej płatności.
     *
     * Metoda jest transakcyjna, bo zmiana statusu rezerwacji powinna zostać
     * zapisana atomowo.
     *
     * Flow:
     *
     * 1. Pobierz rezerwację z relacjami event i customer.
     * 2. Wywołaj metodę domenową reservation.confirm().
     * 3. Jeśli status nie pozwala na potwierdzenie, zamień IllegalStateException
     *    na BusinessRuleException.
     * 4. Zwróć DTO odpowiedzi.
     *
     * Nie ma jawnego reservationRepository.save(...), bo encja jest zarządzana
     * przez persistence context. Hibernate zapisze zmianę przy flush/commit.
     */
    @Transactional
    public ReservationResponse confirmAfterPayment(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));

        try {
            reservation.confirm();
        } catch (IllegalStateException e) {
            /*
             * IllegalStateException pochodzi z encji domenowej.
             *
             * Na granicy serwisu zamieniamy ją na BusinessRuleException,
             * żeby GlobalExceptionHandler mógł zwrócić spójny błąd biznesowy.
             */
            throw new BusinessRuleException(e.getMessage());
        }

        return toResponse(reservation);
    }

    /**
     * Oznacza rezerwację jako PAYMENT_TIMEOUT.
     *
     * Używane wtedy, gdy payment provider nie odpowie w wymaganym czasie
     * albo wystąpi błąd techniczny po stronie providera.
     *
     * Ważny niuans biznesowy:
     * PAYMENT_TIMEOUT nie musi oznaczać anulowania rezerwacji.
     * W tym modelu oznacza raczej, że płatność nie została rozstrzygnięta
     * w oczekiwanym czasie.
     *
     * Jeśli timeout ma definitywnie zwalniać miejsce, trzeba dodatkowo wywołać
     * logikę zwolnienia capacity.
     */
    @Transactional
    public ReservationResponse markPaymentTimeout(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));

        try {
            reservation.markPaymentTimeout();
        } catch (IllegalStateException e) {
            throw new BusinessRuleException(e.getMessage());
        }

        return toResponse(reservation);
    }

    /**
     * Pobiera aktualny stan rezerwacji.
     *
     * readOnly = true informuje Spring/Hibernate, że metoda nie powinna zmieniać
     * danych. To dobre dla odczytów i może ograniczyć część narzutów.
     */
    @Transactional(readOnly = true)
    public ReservationResponse get(UUID reservationId) {
        Reservation reservation = reservationRepository.findDetailedById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found: " + reservationId));

        return toResponse(reservation);
    }

    /**
     * Mapuje encję Reservation na DTO odpowiedzi.
     *
     * Ta metoda dotyka relacji:
     * - reservation.getEvent(),
     * - reservation.getCustomer().
     *
     * Dlatego repozytorium używa findDetailedById(...), które powinno pobrać
     * wymagane relacje przez EntityGraph.
     */
    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getEvent().getId(),
                reservation.getEvent().getName(),
                reservation.getCustomer().getId(),
                reservation.getCustomer().getEmail(),
                reservation.getStatus(),
                reservation.getCreatedAt(),
                reservation.getConfirmedAt(),
                reservation.getCancelledAt()
        );
    }
}