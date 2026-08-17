package com.example.urlshortener.analytics;

import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.repository.ShortUrlRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serwis odpowiedzialny za przygotowywanie danych dla dashboardu analitycznego.
 *
 * <p>
 * Ta klasa agreguje dane pochodzące z kilku źródeł:
 * </p>
 *
 * <ul>
 *     <li>{@link ShortUrlRepository} — informacje o skróconych URL-ach,</li>
 *     <li>{@link ClickEventRepository} — surowe eventy kliknięć,</li>
 *     <li>{@link DailyUrlStatsRepository} — agregaty dzienne,</li>
 *     <li>{@link ClickCounterService} — szybkie liczniki kliknięć z Redisa,</li>
 *     <li>{@link Clock} — aktualna data/czas w testowalny sposób.</li>
 * </ul>
 *
 * <p>
 * Serwis jest używany przez {@link DashboardController}. Kontroler przyjmuje request HTTP,
 * a ta klasa wykonuje właściwą logikę aplikacyjną: waliduje istnienie short code,
 * ustala zakres dat, pobiera dane i składa odpowiedzi DTO.
 * </p>
 *
 * <p>
 * Ważne: ta klasa jest warstwą read-model/dashboardową. Nie powinna wykonywać zmian
 * stanu linków ani zapisywać eventów kliknięć. Od tego są inne serwisy, np.
 * {@code AnalyticsService} albo {@code ShortUrlService}.
 * </p>
 */
@Service
public class DashboardService {

    /**
     * Repozytorium skróconych URL-i.
     *
     * <p>
     * Używane tutaj do:
     * </p>
     *
     * <ul>
     *     <li>sprawdzenia, czy dany short code istnieje,</li>
     *     <li>policzenia wszystkich URL-i w systemie,</li>
     *     <li>policzenia URL-i według statusu, np. ACTIVE lub BLOCKED.</li>
     * </ul>
     */
    private final ShortUrlRepository shortUrlRepository;

    /**
     * Repozytorium surowych eventów kliknięć.
     *
     * <p>
     * Używane do zliczania kliknięć, podejrzanych kliknięć oraz pobierania
     * rankingów wymiarów analitycznych, takich jak kraje, urządzenia, przeglądarki
     * i referrery.
     * </p>
     */
    private final ClickEventRepository clickEventRepository;

    /**
     * Repozytorium dziennych agregatów kliknięć.
     *
     * <p>
     * Używane do odczytu serii czasowej kliknięć dzień po dniu bez liczenia
     * wszystkiego z tabeli surowych eventów.
     * </p>
     */
    private final DailyUrlStatsRepository dailyUrlStatsRepository;

    /**
     * Serwis szybkich liczników kliknięć z Redisa.
     *
     * <p>
     * Jeśli licznik Redis jest dostępny, dashboard może pokazać świeższą albo
     * szybszą wartość. Jeśli licznik nie istnieje albo Redis jest niedostępny,
     * kod robi fallback do trwałych danych w bazie.
     * </p>
     */
    private final ClickCounterService clickCounterService;

    /**
     * Zegar używany do wyliczania aktualnej daty.
     *
     * <p>
     * Zamiast wywoływać bezpośrednio {@code LocalDate.now()}, używamy {@link Clock},
     * co ułatwia testy jednostkowe i integracyjne.
     * </p>
     */
    private final Clock clock;

    /**
     * Konstruktor serwisu dashboardu.
     *
     * <p>
     * Wszystkie zależności są wstrzykiwane przez konstruktor. Dzięki temu klasa
     * jest łatwiejsza do testowania i nie ukrywa swoich zależności.
     * </p>
     *
     * @param shortUrlRepository repozytorium skróconych URL-i
     * @param clickEventRepository repozytorium eventów kliknięć
     * @param dailyUrlStatsRepository repozytorium agregatów dziennych
     * @param clickCounterService serwis liczników Redis
     * @param clock zegar aplikacji
     */
    public DashboardService(
            ShortUrlRepository shortUrlRepository,
            ClickEventRepository clickEventRepository,
            DailyUrlStatsRepository dailyUrlStatsRepository,
            ClickCounterService clickCounterService,
            Clock clock
    ) {
        this.shortUrlRepository = shortUrlRepository;
        this.clickEventRepository = clickEventRepository;
        this.dailyUrlStatsRepository = dailyUrlStatsRepository;
        this.clickCounterService = clickCounterService;
        this.clock = clock;
    }

    /**
     * Przygotowuje szczegółową analitykę dla jednego short code.
     *
     * <p>
     * Metoda jest transakcyjna w trybie read-only, ponieważ wykonuje wyłącznie
     * odczyty z bazy danych. Taka adnotacja pomaga Hibernate/Springowi zoptymalizować
     * pracę sesji i jest czytelną informacją projektową.
     * </p>
     *
     * <p>
     * Zakres dat:
     * </p>
     *
     * <ul>
     *     <li>jeśli {@code to} nie podano, przyjmowana jest dzisiejsza data,</li>
     *     <li>jeśli {@code from} nie podano, przyjmowane jest ostatnie 7 dni
     *     liczone jako {@code safeTo.minusDays(6)} do {@code safeTo},</li>
     *     <li>obie daty są traktowane jako zakres inkluzywny na poziomie dni.</li>
     * </ul>
     *
     * <p>
     * Metoda zwraca między innymi:
     * </p>
     *
     * <ul>
     *     <li>total clicks,</li>
     *     <li>suspicious clicks,</li>
     *     <li>dzienną serię kliknięć,</li>
     *     <li>top kraje,</li>
     *     <li>top urządzenia,</li>
     *     <li>top przeglądarki,</li>
     *     <li>top referrery.</li>
     * </ul>
     *
     * @param shortCode kod skróconego linku
     * @param from opcjonalna data początkowa zakresu
     * @param to opcjonalna data końcowa zakresu
     * @return odpowiedź DTO z analityką URL-a
     * @throws ShortUrlNotFoundException jeśli short code nie istnieje
     */
    @Transactional(readOnly = true)
    public UrlAnalyticsResponse analyticsFor(String shortCode, LocalDate from, LocalDate to) {

        /*
         * Najpierw sprawdzamy, czy short code istnieje.
         *
         * Dzięki temu dashboard nie zwraca pustych statystyk dla nieistniejącego
         * linku, tylko jednoznaczny błąd domenowy.
         */
        if (!shortUrlRepository.existsByShortCode(shortCode)) {
            throw new ShortUrlNotFoundException(shortCode);
        }

        /*
         * Ustalamy bezpieczną datę końcową zakresu.
         *
         * Jeśli użytkownik nie podał parametru "to", przyjmujemy dzisiejszą datę
         * według wstrzykniętego zegara.
         */
        LocalDate safeTo = to == null ? LocalDate.now(clock) : to;

        /*
         * Ustalamy bezpieczną datę początkową zakresu.
         *
         * Jeśli użytkownik nie podał parametru "from", domyślnie zwracamy zakres
         * 7 dni: safeTo - 6 dni, safeTo.
         *
         * Przykład:
         * safeTo = 2026-06-07
         * safeFrom = 2026-06-01
         */
        LocalDate safeFrom = from == null ? safeTo.minusDays(6) : from;

        /*
         * Zamieniamy datę początkową na Instant w UTC.
         *
         * safeFrom.atStartOfDay() oznacza początek dnia, czyli 00:00:00.
         */
        var fromInstant = safeFrom.atStartOfDay().toInstant(ZoneOffset.UTC);

        /*
         * Zamieniamy datę końcową na Instant w UTC.
         *
         * Obecna implementacja robi koniec dnia jako:
         *
         * safeTo + 1 dzień, początek dnia, minus 1 nanosekunda.
         *
         * Dzięki temu zapytanie "between :from and :to" obejmuje cały dzień safeTo.
         *
         * Uwaga: w zapytaniach produkcyjnych często lepiej użyć zakresu półotwartego:
         *
         * clickedAt >= fromInstant AND clickedAt < exclusiveToInstant
         *
         * gdzie exclusiveToInstant = safeTo.plusDays(1).atStartOfDay().
         * Pozwala to uniknąć problemów z granicami zakresów.
         */
        var toInstant = safeTo.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC);

        /*
         * Limit wyników dla rankingów top dimensions.
         *
         * PageRequest.of(0, 10) oznacza pierwszą stronę z maksymalnie 10 wynikami.
         * Używane jest np. dla top krajów, urządzeń, przeglądarek i referrerów.
         */
        var topLimit = PageRequest.of(0, 10);

        /*
         * Pobieramy dzienne agregaty kliknięć dla danego short code i zakresu dat.
         *
         * Repozytorium powinno zwrócić rekordy posortowane rosnąco po dacie.
         */
        Map<LocalDate, DailyUrlStats> statsByDate = dailyUrlStatsRepository
                .findByShortCodeAndDateBetweenOrderByDateAsc(shortCode, safeFrom, safeTo)
                .stream()

                /*
                 * Zamieniamy listę agregatów na mapę:
                 *
                 * date -> DailyUrlStats
                 *
                 * Dzięki temu podczas budowania serii dziennej możemy szybko znaleźć
                 * statystyki dla konkretnej daty.
                 */
                .collect(Collectors.toMap(DailyUrlStats::getDate, Function.identity()));

        /*
         * Budujemy serię dzienną dla każdego dnia w zakresie.
         *
         * datesUntil(safeTo.plusDays(1)) tworzy zakres dat:
         *
         * safeFrom, safeFrom+1, ..., safeTo
         *
         * ponieważ datesUntil() samo w sobie jest exclusive na końcu.
         */
        var daily = safeFrom.datesUntil(safeTo.plusDays(1))
                .map(date -> new DailyClicksResponse(
                        date,

                        /*
                         * Najpierw próbujemy pobrać dzienny licznik z Redisa.
                         *
                         * Jeśli Redis ma wartość, używamy jej.
                         *
                         * Jeśli Redis nie ma wartości, fallbackujemy do agregatu z bazy.
                         * Jeśli agregatu też nie ma, tworzymy tymczasowy obiekt z liczbą 0.
                         */
                        clickCounterService.getDaily(shortCode, date)
                                .orElseGet(() -> statsByDate
                                        .getOrDefault(date, new DailyUrlStats(shortCode, date, 0))
                                        .getClicks())
                ))
                .toList();

        /*
         * Pobieramy total clicks.
         *
         * Preferujemy szybki licznik Redis.
         * Jeśli go nie ma, liczymy eventy z bazy przez countByShortCode().
         */
        long totalClicks = clickCounterService.getTotal(shortCode)
                .orElseGet(() -> clickEventRepository.countByShortCode(shortCode));

        /*
         * Pobieramy liczbę podejrzanych kliknięć dla konkretnego short code.
         *
         * Ta wartość pochodzi z trwałych eventów w bazie.
         */
        long suspiciousClicks = clickEventRepository.countByShortCodeAndSuspiciousTrue(shortCode);

        /*
         * Składamy pełną odpowiedź analityczną.
         *
         * Dodatkowe rankingi są pobierane z ClickEventRepository:
         * - top countries,
         * - top devices,
         * - top browsers,
         * - top referrers.
         */
        return new UrlAnalyticsResponse(
                shortCode,
                totalClicks,
                suspiciousClicks,
                safeFrom,
                safeTo,
                daily,
                clickEventRepository.topCountries(shortCode, fromInstant, toInstant, topLimit),
                clickEventRepository.topDevices(shortCode, fromInstant, toInstant, topLimit),
                clickEventRepository.topBrowsers(shortCode, fromInstant, toInstant, topLimit),
                clickEventRepository.topReferrers(shortCode, fromInstant, toInstant, topLimit)
        );
    }

    /**
     * Przygotowuje globalne podsumowanie dashboardu.
     *
     * <p>
     * Metoda zwraca ogólny stan systemu, np.:
     * </p>
     *
     * <ul>
     *     <li>łączną liczbę URL-i,</li>
     *     <li>liczbę aktywnych URL-i,</li>
     *     <li>liczbę zablokowanych URL-i,</li>
     *     <li>łączną liczbę kliknięć,</li>
     *     <li>liczbę podejrzanych kliknięć,</li>
     *     <li>liczbę kliknięć dzisiaj.</li>
     * </ul>
     *
     * <p>
     * Tak jak {@link #analyticsFor(String, LocalDate, LocalDate)}, metoda działa
     * w transakcji read-only.
     * </p>
     *
     * @return globalne podsumowanie dashboardu
     */
    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {

        /*
         * Dzisiejsza data według zegara aplikacji.
         *
         * Używana do policzenia kliknięć z dzisiaj.
         */
        LocalDate today = LocalDate.now(clock);

        /*
         * Łączna liczba skróconych URL-i w systemie.
         */
        long totalUrls = shortUrlRepository.count();

        /*
         * Liczba aktywnych linków.
         */
        long activeUrls = shortUrlRepository.countByStatus(UrlStatus.ACTIVE);

        /*
         * Liczba zablokowanych linków.
         */
        long blockedUrls = shortUrlRepository.countByStatus(UrlStatus.BLOCKED);

        /*
         * Łączna liczba surowych eventów kliknięć.
         *
         * Uwaga: przy bardzo dużej tabeli click_events count() może być kosztowne.
         * W większym systemie lepiej utrzymywać osobny licznik/agregat.
         */
        long totalClicks = clickEventRepository.count();

        /*
         * Łączna liczba podejrzanych kliknięć w systemie.
         */
        long suspiciousClicks = clickEventRepository.countBySuspiciousTrue();

        /*
         * Liczba kliknięć dzisiaj.
         *
         * Obecna implementacja pobiera wszystkie DailyUrlStats przez findAll(),
         * następnie filtruje je w pamięci po dacie dzisiejszej i sumuje kliknięcia.
         *
         * To jest poprawne dla małych danych, ale nieoptymalne przy większej skali.
         * Lepsze byłoby dedykowane zapytanie w repozytorium, np.:
         *
         * SELECT COALESCE(SUM(clicks), 0)
         * FROM url_daily_stats
         * WHERE date = :today
         */
        long clicksToday = dailyUrlStatsRepository.findAll().stream()
                .filter(stats -> stats.getDate().equals(today))
                .mapToLong(DailyUrlStats::getClicks)
                .sum();

        /*
         * Zwracamy DTO z globalnym podsumowaniem dashboardu.
         */
        return new DashboardSummaryResponse(
                totalUrls,
                activeUrls,
                blockedUrls,
                totalClicks,
                suspiciousClicks,
                clicksToday,
                today
        );
    }
}