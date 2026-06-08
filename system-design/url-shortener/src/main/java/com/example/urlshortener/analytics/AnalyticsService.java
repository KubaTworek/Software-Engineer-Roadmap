package com.example.urlshortener.analytics;

import com.example.urlshortener.abuse.AbuseDetectionService;
import com.example.urlshortener.abuse.AbuseVerdict;
import com.example.urlshortener.queue.ClickMessage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serwis odpowiedzialny za przetwarzanie analityki kliknięć.
 *
 * <p>
 * Ta klasa obsługuje zdarzenia kliknięć przychodzące z kolejki, reprezentowane
 * przez {@link ClickMessage}. Nie jest wywoływana bezpośrednio przez endpoint
 * redirectu w ścieżce synchronicznej. Dzięki temu przekierowanie użytkownika
 * może zakończyć się szybko, a analityka jest zapisywana asynchronicznie.
 * </p>
 *
 * <p>
 * Główne odpowiedzialności tej klasy:
 * </p>
 *
 * <ul>
 *     <li>sprawdzenie, czy analityka jest włączona,</li>
 *     <li>deduplikacja eventów po {@code eventId},</li>
 *     <li>uruchomienie abuse detection,</li>
 *     <li>zahashowanie adresu IP,</li>
 *     <li>normalizacja i ograniczenie długości danych wejściowych,</li>
 *     <li>zapis surowego eventu kliknięcia do tabeli {@code click_events},</li>
 *     <li>aktualizacja agregatów dziennych w tabeli {@code url_daily_stats},</li>
 *     <li>aktualizacja szybkich liczników kliknięć, np. w Redisie.</li>
 * </ul>
 *
 * <p>
 * Serwis jest oznaczony jako {@link Service}, więc Spring rejestruje go jako bean
 * aplikacyjny i może wstrzykiwać go np. do consumerów kolejki RabbitMQ.
 * </p>
 *
 * <p>
 * Metoda {@link #processClick(ClickMessage)} jest transakcyjna. Oznacza to, że
 * zapis eventu oraz aktualizacja agregatu dziennego są wykonywane w jednej
 * transakcji bazodanowej. Liczniki w Redisie są jednak poza transakcją bazy danych.
 * </p>
 */
@Service
public class AnalyticsService {

    /**
     * Logger diagnostyczny.
     *
     * <p>
     * W tej klasie używany głównie do:
     * </p>
     *
     * <ul>
     *     <li>logowania pominięcia duplikatu eventu,</li>
     *     <li>logowania błędów podczas zapisu analityki,</li>
     *     <li>pomocy w diagnozowaniu problemów z consumerem kolejki.</li>
     * </ul>
     */
    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    /**
     * Repozytorium surowych eventów kliknięć.
     *
     * <p>
     * Odpowiada za zapis do tabeli przechowującej pojedyncze kliknięcia.
     * Każdy event powinien mieć unikalne {@code eventId}, które umożliwia
     * deduplikację wiadomości z kolejki.
     * </p>
     */
    private final ClickEventRepository clickEventRepository;

    /**
     * Repozytorium agregatów dziennych.
     *
     * <p>
     * Agregaty dzienne pozwalają szybko pokazać liczbę kliknięć dla short code
     * w konkretnym dniu bez kosztownego liczenia wszystkiego z tabeli surowych
     * eventów.
     * </p>
     */
    private final DailyUrlStatsRepository dailyUrlStatsRepository;

    /**
     * Serwis szybkich liczników kliknięć.
     *
     * <p>
     * Najczęściej taka klasa opiera się o Redis i przechowuje szybkie liczniki:
     * </p>
     *
     * <ul>
     *     <li>total clicks dla short code,</li>
     *     <li>daily clicks dla short code i daty.</li>
     * </ul>
     *
     * <p>
     * Liczniki te są przydatne do szybkiego dashboardu lub metryk near-real-time.
     * </p>
     */
    private final ClickCounterService clickCounterService;

    /**
     * Konfiguracja modułu analityki.
     *
     * <p>
     * Zawiera między innymi flagę {@code enabled} oraz salt używany do hashowania IP.
     * </p>
     */
    private final AnalyticsProperties properties;

    /**
     * Serwis wykrywania nadużyć.
     *
     * <p>
     * Przed zapisaniem eventu serwis analizuje kliknięcie i zwraca werdykt:
     * czyste, podejrzane lub powodujące auto-blocking.
     * </p>
     *
     * <p>
     * Wynik abuse detection jest zapisywany razem z eventem, dzięki czemu dashboard
     * może później pokazać liczbę podejrzanych kliknięć i przyczyny oznaczeń.
     * </p>
     */
    private final AbuseDetectionService abuseDetectionService;

    /**
     * Konstruktor z dependency injection.
     *
     * <p>
     * Wszystkie zależności są jawnie przekazywane przez konstruktor, co ułatwia
     * testowanie klasy oraz utrzymanie kodu.
     * </p>
     */
    public AnalyticsService(
            ClickEventRepository clickEventRepository,
            DailyUrlStatsRepository dailyUrlStatsRepository,
            ClickCounterService clickCounterService,
            AnalyticsProperties properties,
            AbuseDetectionService abuseDetectionService
    ) {
        this.clickEventRepository = clickEventRepository;
        this.dailyUrlStatsRepository = dailyUrlStatsRepository;
        this.clickCounterService = clickCounterService;
        this.properties = properties;
        this.abuseDetectionService = abuseDetectionService;
    }

    /**
     * Przetwarza pojedynczy event kliknięcia.
     *
     * <p>
     * To jest główna metoda tej klasy. Zwykle jest wywoływana przez consumer
     * wiadomości z kolejki, np. RabbitMQ.
     * </p>
     *
     * <p>
     * Metoda działa w transakcji bazodanowej:
     * </p>
     *
     * <ul>
     *     <li>zapis {@code ClickEvent},</li>
     *     <li>aktualizacja {@code DailyUrlStats}</li>
     * </ul>
     *
     * <p>
     * są wykonywane jako jedna jednostka pracy.
     * </p>
     *
     * <p>
     * Ogólny przepływ:
     * </p>
     *
     * <ol>
     *     <li>Jeśli analytics jest wyłączone, kończy działanie.</li>
     *     <li>Sprawdza, czy event o takim {@code eventId} już istnieje.</li>
     *     <li>Uruchamia abuse detection.</li>
     *     <li>Wylicza datę dzienną w UTC.</li>
     *     <li>Hashuje adres IP.</li>
     *     <li>Zapisuje surowy event kliknięcia.</li>
     *     <li>Aktualizuje agregat dzienny.</li>
     *     <li>Zwiększa szybkie liczniki kliknięć.</li>
     * </ol>
     *
     * @param message wiadomość kliknięcia pobrana z kolejki
     */
    @Transactional
    public void processClick(ClickMessage message) {

        /*
         * Jeśli moduł analytics jest wyłączony w konfiguracji, event jest ignorowany.
         *
         * To przydatne np. w środowisku lokalnym, testowym, albo gdy trzeba
         * awaryjnie odciążyć bazę/analitykę.
         */
        if (!properties.enabled()) {
            return;
        }

        try {
            /*
             * Deduplikacja logiczna po eventId.
             *
             * Kolejki wiadomości często działają w modelu at-least-once delivery.
             * Oznacza to, że ta sama wiadomość może zostać dostarczona więcej
             * niż raz, np. po restarcie consumera albo timeoutcie ack.
             *
             * Dlatego przed zapisem sprawdzamy, czy event o danym eventId
             * już istnieje.
             */
            if (clickEventRepository.existsByEventId(message.eventId())) {
                log.debug("Skipping duplicate click eventId={}", message.eventId());
                return;
            }

            /*
             * Uruchamiamy abuse detection.
             *
             * Werdykt zawiera m.in.:
             * - czy kliknięcie jest podejrzane,
             * - powód podejrzenia,
             * - czy doszło do auto-blockingu,
             * - device type,
             * - browser.
             */
            AbuseVerdict verdict = abuseDetectionService.evaluate(message);

            /*
             * Wyliczamy datę kliknięcia w UTC.
             *
             * Jest to ważne, ponieważ agregaty dzienne powinny być liczone
             * w jednej, spójnej strefie czasowej, niezależnie od regionu serwera
             * albo lokalizacji użytkownika.
             */
            LocalDate date = message.clickedAt().atZone(ZoneOffset.UTC).toLocalDate();

            /*
             * Hashujemy IP przed zapisem.
             *
             * Nie zapisujemy surowego adresu IP, żeby ograniczyć ilość danych
             * osobowych w bazie. Hash jest liczony z saltem z konfiguracji.
             */
            String ipHash = hashIp(message.ipAddress());

            /*
             * Zapisujemy surowy event kliknięcia.
             *
             * Część pól jest obcinana metodą truncate(), żeby uniknąć zapisu
             * bardzo długich wartości do bazy, np. ekstremalnie długiego User-Agent
             * albo Referrer.
             *
             * Dane zapisywane w eventcie:
             * - eventId,
             * - shortCode,
             * - timestamp kliknięcia,
             * - hash IP,
             * - User-Agent,
             * - referrer,
             * - domena referera,
             * - kraj,
             * - device type,
             * - browser,
             * - czy kliknięcie podejrzane,
             * - powód podejrzenia.
             */
            clickEventRepository.save(new ClickEvent(
                    message.eventId(),
                    message.shortCode(),
                    message.clickedAt(),
                    ipHash,
                    truncate(message.userAgent(), 2048),
                    truncate(message.referrer(), 2048),
                    truncate(referrerDomain(message.referrer()), 255),
                    normalizeCountry(message.country()),
                    verdict.deviceType(),
                    verdict.browser(),
                    verdict.suspicious(),
                    truncate(verdict.reason(), 2048)
            ));

            /*
             * Przygotowujemy identyfikator agregatu dziennego.
             *
             * Agregat jest identyfikowany przez parę:
             *
             * shortCode + date
             *
             * Czyli jeden short code ma osobny rekord statystyk dla każdego dnia.
             */
            DailyUrlStatsId statsId = new DailyUrlStatsId(message.shortCode(), date);

            /*
             * Pobieramy istniejący agregat dzienny albo tworzymy nowy,
             * jeśli dla tej pary shortCode + date jeszcze go nie ma.
             */
            DailyUrlStats stats = dailyUrlStatsRepository.findById(statsId)
                    .orElseGet(() -> new DailyUrlStats(message.shortCode(), date, 0));

            /*
             * Zwiększamy licznik kliknięć w agregacie dziennym o 1.
             */
            stats.increment(1);

            /*
             * Zapisujemy zaktualizowany agregat dzienny.
             */
            dailyUrlStatsRepository.save(stats);

            /*
             * Zwiększamy szybki licznik total clicks.
             *
             * Ten licznik zwykle znajduje się w Redisie i może być używany do
             * szybkiego wyświetlania statystyk bez odpytywania cięższych tabel.
             */
            clickCounterService.incrementTotal(message.shortCode());

            /*
             * Zwiększamy szybki licznik dzienny.
             */
            clickCounterService.incrementDaily(message.shortCode(), date);

        } catch (DataIntegrityViolationException duplicate) {
            /*
             * Dodatkowa ochrona przed duplikatami.
             *
             * Nawet jeśli wcześniejsze existsByEventId() zwróci false, przy równoległym
             * przetwarzaniu dwóch takich samych wiadomości może dojść do race condition:
             *
             * - consumer A sprawdza exists -> false,
             * - consumer B sprawdza exists -> false,
             * - consumer A zapisuje event,
             * - consumer B próbuje zapisać ten sam event i dostaje constraint violation.
             *
             * Dlatego baza danych powinna mieć unikalny constraint na eventId,
             * a ten catch traktuje taki przypadek jako duplikat, nie jako błąd systemowy.
             */
            log.debug("Skipping duplicate click eventId={}", message.eventId());
        } catch (Exception exception) {
            /*
             * Każdy inny błąd oznacza, że przetwarzanie eventu się nie powiodło.
             *
             * Rzucamy IllegalStateException, żeby listener kolejki mógł potraktować
             * wiadomość jako nieprzetworzoną. Przy trwałym błędzie Rabbit listener
             * może wysłać wiadomość do DLQ.
             */
            log.warn(
                    "Failed to persist queued click analytics eventId={} shortCode={}",
                    message.eventId(),
                    message.shortCode(),
                    exception
            );

            throw new IllegalStateException(exception);
        }
    }

    /**
     * Hashuje adres IP z użyciem SHA-256 oraz salta z konfiguracji.
     *
     * <p>
     * Celem jest ograniczenie przechowywania surowych danych osobowych.
     * Zamiast zapisywać IP bezpośrednio, zapisujemy jego hash.
     * </p>
     *
     * <p>
     * Hash liczony jest z wartości:
     * </p>
     *
     * <pre>
     * salt + ":" + ipAddress
     * </pre>
     *
     * <p>
     * Dzięki użyciu salta trudniej odtworzyć adres IP metodą słownikową.
     * Sam SHA-256 bez salta byłby słabszy, szczególnie dla IPv4, ponieważ
     * przestrzeń możliwych adresów IPv4 jest ograniczona.
     * </p>
     *
     * <p>
     * Uwaga: dla jeszcze lepszego bezpieczeństwa produkcyjnego można rozważyć
     * HMAC-SHA256 z sekretem zamiast zwykłego hasha z saltem.
     * </p>
     *
     * @param ipAddress surowy adres IP z eventu kliknięcia
     * @return hash IP w formacie hex albo {@code null}, jeśli IP nie podano
     * @throws Exception jeśli algorytm SHA-256 byłby niedostępny
     */
    private String hashIp(String ipAddress) throws Exception {

        /*
         * Jeśli IP jest puste albo nieobecne, nie zapisujemy żadnego hasha.
         */
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }

        /*
         * Tworzymy instancję algorytmu SHA-256.
         */
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        /*
         * Budujemy wejście do hashowania.
         *
         * Dodajemy salt z konfiguracji i separator ":".
         *
         * Używamy UTF-8 jawnie, żeby wynik był niezależny od domyślnego charsetu JVM.
         */
        byte[] hash = digest.digest(
                (properties.ipHashSalt() + ":" + ipAddress).getBytes(StandardCharsets.UTF_8)
        );

        /*
         * Zamieniamy wynik binarny na czytelny string hexadecimalny.
         */
        return HexFormat.of().formatHex(hash);
    }

    /**
     * Wyciąga domenę z nagłówka referrer.
     *
     * <p>
     * Przykład:
     * </p>
     *
     * <pre>
     * https://sub.example.com/path?q=1 -> sub.example.com
     * </pre>
     *
     * <p>
     * Jeśli referrer jest pusty, niepoprawny albo nie zawiera hosta,
     * metoda zwraca {@code null}.
     * </p>
     *
     * @param referrer wartość nagłówka HTTP Referer/Referrer
     * @return domena referera w lowercase albo {@code null}
     */
    private String referrerDomain(String referrer) {

        /*
         * Brak referrera nie jest błędem.
         *
         * Wiele normalnych kliknięć nie ma referrera:
         * - wejście bezpośrednie,
         * - komunikatory,
         * - aplikacje mobilne,
         * - restrykcyjne ustawienia prywatności,
         * - polityka Referrer-Policy strony źródłowej.
         */
        if (referrer == null || referrer.isBlank()) {
            return null;
        }

        try {
            /*
             * Parsujemy referrer jako URI i pobieramy hosta.
             */
            String host = URI.create(referrer).getHost();

            /*
             * Jeśli host istnieje, normalizujemy go do lowercase.
             */
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            /*
             * Niepoprawny referrer ignorujemy.
             *
             * Nie powinien on zatrzymywać przetwarzania analytics.
             */
            return null;
        }
    }

    /**
     * Normalizuje kod kraju do standardowej postaci używanej w analytics.
     *
     * <p>
     * Jeśli kraj nie został podany, zwraca {@code unknown}.
     * Jeśli został podany, usuwa spacje z początku/końca i zamienia tekst
     * na wielkie litery.
     * </p>
     *
     * <p>
     * Przykłady:
     * </p>
     *
     * <pre>
     * "pl"   -> "PL"
     * " us " -> "US"
     * null   -> "unknown"
     * ""     -> "unknown"
     * </pre>
     *
     * @param country kod kraju z eventu kliknięcia
     * @return znormalizowany kod kraju albo {@code unknown}
     */
    private String normalizeCountry(String country) {

        /*
         * Brak kraju zapisujemy jako "unknown", żeby agregacje analytics miały
         * spójną wartość zamiast null.
         */
        if (country == null || country.isBlank()) {
            return "unknown";
        }

        /*
         * Usuwamy białe znaki i normalizujemy do uppercase.
         */
        return country.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Obcina tekst do maksymalnej długości.
     *
     * <p>
     * Jest to defensywna ochrona przed zapisaniem ekstremalnie długich wartości,
     * np. bardzo długiego User-Agent, Referrera albo powodu abuse detection.
     * </p>
     *
     * <p>
     * Takie dane mogą pochodzić od użytkownika lub klienta HTTP, więc nie należy
     * zakładać, że będą miały rozsądną długość.
     * </p>
     *
     * @param value tekst wejściowy
     * @param maxLength maksymalna liczba znaków
     * @return oryginalny tekst albo jego skrócona wersja; {@code null}, jeśli wejście było {@code null}
     */
    private String truncate(String value, int maxLength) {

        /*
         * Null pozostaje nullem.
         *
         * Dzięki temu metoda nie zamienia braku wartości na pusty string.
         */
        if (value == null) {
            return null;
        }

        /*
         * Jeśli tekst mieści się w limicie, zwracamy go bez zmian.
         * W przeciwnym razie zwracamy prefiks o długości maxLength.
         */
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}