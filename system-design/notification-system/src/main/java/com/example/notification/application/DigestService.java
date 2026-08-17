package com.example.notification.application;

import com.example.notification.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za mechanizm digestów.
 *
 * Digest służy do grupowania wielu małych zdarzeń w jedno zbiorcze powiadomienie.
 *
 * Przykład:
 * Zamiast wysyłać użytkownikowi 10 osobnych emaili:
 * - "Anna skomentowała Twój post",
 * - "Piotr skomentował Twój post",
 * - "Maria skomentowała Twój post",
 *
 * system buforuje te zdarzenia i po określonym czasie wysyła jedno powiadomienie:
 * - "Masz 10 nowych komentarzy".
 *
 * Ten serwis NIE wysyła wiadomości bezpośrednio do providera.
 * Po zakończeniu okna digestu tworzy normalne Notification przez NotificationService.
 *
 * Dalej działa standardowy pipeline:
 * NotificationService -> OutboxEvent -> OutboxPublisher -> Queue -> NotificationWorker -> Provider.
 */
@Service
public class DigestService {
    private final Ports.DigestRepository digestRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    /**
     * Długość okna digestu w sekundach.
     *
     * Jeśli windowSeconds = 30, to pierwsze zdarzenie tworzy bufor,
     * który będzie flushowany po około 30 sekundach.
     *
     * Wszystkie kolejne zdarzenia dla tego samego:
     * - tenantId,
     * - userId,
     * - digestKey,
     *
     * trafią do tego samego bufora, dopóki nie zostanie on oznaczony jako flushed.
     */
    private final long windowSeconds;

    public DigestService(
            Ports.DigestRepository digestRepository,
            NotificationService notificationService,
            AuditService auditService,
            @Value("${notification.digest.window-seconds:30}") long windowSeconds
    ) {
        this.digestRepository = digestRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Dodaje pojedyncze zdarzenie do bufora digestu.
     *
     * To jest metoda wywoływana wtedy, gdy aplikacja chce "odłożyć" powiadomienie
     * i potencjalnie połączyć je z innymi podobnymi zdarzeniami.
     *
     * Przykład:
     * digestKey = "comments"
     * item = { "author": "Anna", "postId": "post-123" }
     *
     * System buforuje item, zamiast od razu wysyłać osobne powiadomienie.
     */
    public DigestBuffer buffer(
            String tenantId,
            String actor,
            String userId,
            String digestKey,
            Map<String, Object> item
    ) {
        /*
         * Szukamy otwartego bufora dla kombinacji:
         * - tenantId,
         * - userId,
         * - digestKey.
         *
         * digestKey rozdziela różne typy agregacji.
         *
         * Przykłady digestKey:
         * - comments,
         * - likes,
         * - mentions,
         * - weekly-summary.
         *
         * Dzięki temu komentarze i lajki nie mieszają się w jednym digescie.
         */
        DigestBuffer buffer = digestRepository
                .findOpen(tenantId, userId, digestKey)

                /*
                 * Jeśli nie ma otwartego bufora, tworzymy nowy.
                 *
                 * flushAt ustawiamy na teraz + windowSeconds.
                 * To oznacza, że scheduler będzie mógł wysłać digest dopiero
                 * po upłynięciu tego okna.
                 */
                .orElseGet(() -> new DigestBuffer(
                        UUID.randomUUID(),
                        tenantId,
                        userId,
                        digestKey,
                        Instant.now().plusSeconds(windowSeconds)
                ));

        /*
         * Dodajemy nowe zdarzenie do bufora.
         *
         * Sam item jest elastyczną mapą, bo różne digesty mogą mieć różne dane.
         * Np. komentarz ma author/postId, a alert może mieć severity/message.
         */
        buffer.addItem(item);

        /*
         * Zapisujemy bufor.
         *
         * W tej wersji repozytorium jest in-memory.
         * W produkcji to powinien być trwały storage, np. PostgreSQL/Redis,
         * bo utrata bufora oznacza utratę informacji o zdarzeniach.
         */
        digestRepository.save(buffer);

        /*
         * Audytujemy dodanie zdarzenia do digestu.
         *
         * To pomaga sprawdzić, czy zdarzenia rzeczywiście są agregowane,
         * zamiast być wysyłane pojedynczo.
         */
        auditService.record(
                tenantId,
                actor,
                AuditAction.DIGEST_BUFFERED,
                buffer.getId(),
                Map.of("digestKey", digestKey)
        );

        return buffer;
    }

    /**
     * Cyklicznie flushuje gotowe digesty.
     *
     * Scheduler uruchamia tę metodę co:
     *
     * notification.digest.fixed-delay-ms
     *
     * Domyślnie w projekcie:
     * - co 2000 ms.
     *
     * Metoda szuka buforów, których flushAt <= now,
     * i zamienia każdy taki bufor w jedno zbiorcze Notification.
     */
    @Scheduled(fixedDelayString = "${notification.digest.fixed-delay-ms:2000}")
    public void flushReady() {
        /*
         * Pobieramy digesty gotowe do wysłania.
         *
         * Repository powinno zwrócić tylko takie bufory, które:
         * - nie są flushed,
         * - mają flushAt w przeszłości albo teraz.
         */
        for (DigestBuffer buffer : digestRepository.findReadyToFlush(Instant.now())) {

            /*
             * Dodatkowe zabezpieczenie przed podwójnym flushowaniem.
             *
             * W środowisku rozproszonym to nie wystarczy.
             * Produkcyjnie potrzebny byłby lock, status transition z warunkiem
             * albo atomiczna operacja w bazie.
             */
            if (buffer.isFlushed()) {
                continue;
            }

            /*
             * Budujemy payload zbiorczego powiadomienia.
             *
             * itemCount mówi, ile zdarzeń zebrano.
             * digestKey mówi, jakiego typu był digest.
             * items zawiera surową listę zdarzeń jako tekst.
             *
             * W lepszej wersji produkcyjnej items powinno być strukturą JSON,
             * a template renderer powinien umieć renderować listę elementów.
             */
            Map<String, Object> payload = Map.of(
                    "itemCount", buffer.getItems().size(),
                    "digestKey", buffer.getDigestKey(),
                    "items", buffer.getItems().toString()
            );

            /*
             * Tworzymy normalne Notification typu WEEKLY_DIGEST.
             *
             * To jest ważne:
             * Digest nie ma osobnego pipeline’u wysyłkowego.
             * Po zbudowaniu payloadu używa standardowego NotificationService.
             *
             * Dzięki temu digest korzysta z:
             * - idempotencji,
             * - deduplikacji,
             * - preferencji,
             * - walidacji template’ów,
             * - outboxa,
             * - kolejki,
             * - workera,
             * - retry,
             * - DLQ,
             * - fallback providerów.
             */
            notificationService.create(
                    buffer.getTenantId(),
                    "system",
                    buffer.getUserId(),
                    NotificationType.WEEKLY_DIGEST,

                    /*
                     * Digest wysyłamy domyślnie przez EMAIL i IN_APP.
                     *
                     * To jest decyzja produktowa.
                     * Można ją później wynieść do konfiguracji.
                     */
                    List.of(Channel.EMAIL, Channel.IN_APP),

                    /*
                     * ContactPoint jest tutaj symulowany.
                     *
                     * W prawdziwej produkcji nie powinno się tworzyć emaila
                     * przez userId + "@example.com".
                     *
                     * Dane kontaktowe powinny pochodzić z:
                     * - User Service,
                     * - Profile Service,
                     * - albo lokalnej kopii contact points.
                     */
                    new ContactPoint(
                            buffer.getUserId() + "@example.com",
                            "+48123123123",
                            "push-" + buffer.getUserId()
                    ),

                    payload,

                    /*
                     * Idempotency key dla digestu.
                     *
                     * Dzięki temu ponowny flush tego samego bufora
                     * nie powinien utworzyć drugiego Notification.
                     */
                    "digest:" + buffer.getId(),

                    /*
                     * Brak expiresAt.
                     *
                     * Digest nie wygasa w tej wersji.
                     * Można dodać expiresAt, jeśli digest po czasie traci sens.
                     */
                    null
            );

            /*
             * Oznaczamy bufor jako flushed.
             *
             * To zapobiega ponownemu przetwarzaniu tego samego bufora
             * przy kolejnym przebiegu schedulera.
             */
            buffer.markFlushed();

            /*
             * Zapisujemy zaktualizowany bufor.
             */
            digestRepository.save(buffer);

            /*
             * Audytujemy flush digestu.
             *
             * Zapisujemy liczbę elementów, które zostały zebrane w digest.
             */
            auditService.record(
                    buffer.getTenantId(),
                    "system",
                    AuditAction.DIGEST_FLUSHED,
                    buffer.getId(),
                    Map.of("items", buffer.getItems().size())
            );
        }
    }
}