package com.example.videostreaming.qoe;

import com.example.videostreaming.messaging.VideoEvents;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumer eventów QoE z kolejki RabbitMQ.
 *
 * QoE = Quality of Experience.
 *
 * Ten komponent działa asynchronicznie względem API.
 * QoeController tylko przyjmuje event z playera i wrzuca go do kolejki,
 * a ten consumer zapisuje go później do bazy/analityki.
 *
 * Główna odpowiedzialność:
 * - odebrać event QoE z kolejki,
 * - zapisać go jako QoeEvent,
 * - policzyć metryki ingestu,
 * - zgłosić błąd, jeśli eventu nie da się przetworzyć.
 *
 * Ważne:
 * Dzięki temu player nie czeka na zapis analityczny.
 * API może szybko odpowiedzieć QUEUED, a cięższe przetwarzanie dzieje się w tle.
 */
@Component
public class QoeConsumer {

    private static final Logger log = LoggerFactory.getLogger(QoeConsumer.class);

    /**
     * Repozytorium eventów QoE.
     *
     * W tej wersji zapisuje eventy do lokalnej bazy.
     * Produkcyjnie takie dane często trafiają do data warehouse,
     * data lake albo osobnego pipeline'u analitycznego.
     */
    private final QoeEventRepository events;

    /**
     * Konfiguracja QoE.
     *
     * Pozwala włączyć albo wyłączyć przetwarzanie eventów QoE
     * bez usuwania całego endpointu i kolejki.
     */
    private final QoeProperties props;

    /**
     * Licznik poprawnie zapisanych eventów QoE.
     *
     * Przydatny w Prometheus/Grafana do monitorowania,
     * czy pipeline analityczny realnie przyjmuje dane.
     */
    private final Counter ingested;

    /**
     * Licznik eventów, których nie udało się przetworzyć.
     *
     * Wzrost tej metryki oznacza problem z bazą, formatem eventu,
     * migracją schematu albo samym consumerem.
     */
    private final Counter failed;

    public QoeConsumer(QoeEventRepository events,
                       QoeProperties props,
                       MeterRegistry meterRegistry) {
        this.events = events;
        this.props = props;

        this.ingested = Counter.builder("video_qoe_events_ingested_total")
                .register(meterRegistry);

        this.failed = Counter.builder("video_qoe_events_failed_total")
                .register(meterRegistry);
    }

    /**
     * Odbiera event QoE z kolejki.
     *
     * Kolejka jest skonfigurowana przez:
     * app.messaging.qoe-queue
     *
     * Typowy flow:
     * 1. Player wysyła event do QoeController.
     * 2. QoeController publikuje event do RabbitMQ.
     * 3. Ten consumer odbiera event.
     * 4. Event jest zapisywany do bazy.
     * 5. Metryka sukcesu zostaje zwiększona.
     *
     * Jeśli zapis się nie powiedzie:
     * - zwiększamy metrykę failed,
     * - logujemy błąd,
     * - rzucamy wyjątek dalej.
     *
     * Rzucenie wyjątku jest celowe:
     * pozwala mechanizmom RabbitMQ/Spring AMQP potraktować wiadomość
     * jako nieprzetworzoną i zastosować retry albo DLQ,
     * zależnie od konfiguracji brokera.
     */
    @RabbitListener(queues = "${app.messaging.qoe-queue}")
    public void consume(VideoEvents.QoePlaybackEvent event) {
        /*
         * Jeśli QoE jest wyłączone w konfiguracji, ignorujemy event.
         *
         * To przydatne lokalnie albo w środowiskach,
         * gdzie nie chcemy zbierać danych analitycznych.
         */
        if (!props.enabled()) {
            return;
        }

        try {
            /*
             * Mapujemy event transportowy z kolejki na encję bazodanową.
             *
             * Nie zapisujemy tutaj wszystkich attributes z eventu,
             * tylko główne pola potrzebne do metryk QoE:
             * startup time, rebuffering, bitrate, CDN, player, device, country.
             */
            events.save(new QoeEvent(
                    event.eventId(),
                    event.userId(),
                    event.videoId(),
                    event.sessionId(),
                    event.eventType(),
                    event.startupTimeMs(),
                    event.rebufferTimeMs(),
                    event.bitrateKbps(),
                    event.cdnProvider(),
                    event.player(),
                    event.deviceType(),
                    event.country(),
                    event.occurredAt()
            ));

            ingested.increment();
        } catch (Exception ex) {
            failed.increment();

            log.error("QoE ingestion failed for event {}", event.eventId(), ex);

            /*
             * Nie połykamy błędu.
             *
             * Jeśli consumer udawałby sukces, event zostałby utracony.
             * Rzucenie wyjątku daje brokerowi szansę na retry/DLQ.
             */
            throw ex;
        }
    }
}