package com.example.videostreaming.personalization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Serwis liczący aktualnie trendujące filmy.
 *
 * Główna odpowiedzialność:
 * - pobiera eventy personalizacji z wybranego okna czasowego,
 * - liczy popularność filmów na podstawie odtworzeń, startów, ukończeń i unikalnych użytkowników,
 * - uwzględnia quality_score z feature store,
 * - zwraca publiczne i opublikowane filmy posortowane po score.
 *
 * To jest ranking globalny, a nie personalizowany.
 * Każdy użytkownik dostaje tę samą listę trending dla danego okna czasowego.
 */
@Service
public class TrendingService {

    /**
     * JdbcTemplate do zapytania agregującego popularność.
     *
     * Używamy SQL bezpośrednio, bo ranking trending to agregacja po eventach,
     * a nie klasyczna operacja CRUD na pojedynczej encji.
     */
    private final JdbcTemplate jdbc;

    /**
     * Konfiguracja personalizacji.
     *
     * Używana m.in. do domyślnego okna czasowego trendów.
     */
    private final PersonalizationProperties properties;

    public TrendingService(JdbcTemplate jdbc, PersonalizationProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /**
     * Zwraca listę trendujących filmów.
     *
     * Flow:
     * 1. Normalizuje okno czasowe.
     * 2. Normalizuje limit wyników.
     * 3. Pobiera publiczne i opublikowane filmy.
     * 4. Dołącza eventy personalizacji z ostatnich N godzin.
     * 5. Dołącza cechy filmu z feature_store_video.
     * 6. Liczy score popularności.
     * 7. Sortuje po score malejąco.
     * 8. Zwraca DTO TrendingResponse.
     *
     * @param windowHours liczba godzin, z których liczymy popularność
     * @param limit maksymalna liczba wyników
     * @return lista trendujących filmów
     */
    public TrendingResponse trending(int windowHours, int limit) {
        /*
         * Normalizacja okna czasowego.
         *
         * Jeśli klient poda 0 albo wartość ujemną, używamy domyślnej konfiguracji.
         * Jeśli poda zbyt duże okno, obcinamy do 30 dni.
         *
         * To chroni bazę przed bardzo ciężkimi zapytaniami po całej historii eventów.
         */
        int hours = windowHours <= 0
                ? properties.trendingWindowHours()
                : Math.min(windowHours, 24 * 30);

        /*
         * Normalizacja limitu.
         *
         * Domyślnie zwracamy 20 pozycji.
         * Maksymalnie pozwalamy na 100, żeby endpoint nie zwracał
         * zbyt dużej odpowiedzi i nie przeciążał bazy.
         */
        int resultLimit = limit <= 0
                ? 20
                : Math.min(limit, 100);

        /*
         * Ranking trending.
         *
         * Score składa się z:
         * - views: podstawowy sygnał popularności,
         * - unique_users: mocniejszy sygnał, bo pokazuje szeroki zasięg,
         * - completions: sygnał jakości/zaangażowania,
         * - quality_score_7d: kara/nagroda za jakość odtwarzania z feature store.
         *
         * Wagi w MVP:
         * - view/start: 1.0,
         * - unique user: 3.0,
         * - completion: 2.0,
         * - quality score: 0.2.
         *
         * Filtrujemy tylko:
         * - PUBLISHED,
         * - PUBLIC.
         *
         * Dzięki temu ranking nie pokazuje szkiców, filmów prywatnych
         * ani materiałów niegotowych do oglądania.
         */
        List<TrendingVideo> items = jdbc.query("""
                select v.id as video_id, v.title,
                       count(*) filter (where e.event_type in ('playback_start','view')) as views,
                       count(*) filter (where e.event_type = 'playback_start') as starts,
                       count(*) filter (where e.event_type = 'playback_complete') as completions,
                       count(distinct e.user_id) as unique_users,
                       count(*) filter (where e.event_type in ('playback_start','view')) * 1.0
                         + count(distinct e.user_id) * 3.0
                         + count(*) filter (where e.event_type = 'playback_complete') * 2.0
                         + coalesce(f.quality_score_7d, 0) * 0.2 as score
                from videos v
                left join personalization_events e on e.video_id = v.id and e.occurred_at >= now() - (? * interval '1 hour')
                left join feature_store_video f on f.video_id = v.id
                where v.status = 'PUBLISHED' and v.visibility = 'PUBLIC'
                group by v.id, v.title, f.quality_score_7d
                order by score desc, v.published_at desc nulls last
                limit ?
                """,
                (rs, rowNum) -> new TrendingVideo(
                        rs.getObject("video_id", UUID.class),
                        rs.getString("title"),
                        rs.getLong("views"),
                        rs.getLong("starts"),
                        rs.getLong("completions"),
                        rs.getLong("unique_users"),
                        rs.getDouble("score")
                ),
                hours,
                resultLimit
        );

        return new TrendingResponse(hours, items);
    }
}