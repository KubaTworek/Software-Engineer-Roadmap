package com.example.videostreaming.personalization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Serwis rekomendacji i rankingu treści.
 *
 * Główna odpowiedzialność:
 * - przypisuje użytkownika do wariantu eksperymentu A/B,
 * - wybiera algorytm rankingu na podstawie wariantu,
 * - pobiera kandydatów rekomendacji,
 * - filtruje filmy już obejrzane przez użytkownika,
 * - liczy końcowy rank_score,
 * - zwraca uporządkowaną listę rekomendacji.
 *
 * Ważne:
 * To jest MVP rekomendacji oparte o SQL i feature store.
 * Nie ma tutaj modelu ML online. Ranking jest liczony regułowo
 * na podstawie score kandydatów, trending_score, quality_score i świeżości filmu.
 */
@Service
public class RecommendationService {

    /**
     * JdbcTemplate do zapytań rankingowych.
     *
     * Rekomendacje są liczone jednym zapytaniem SQL,
     * bo ranking bazuje na połączeniu kilku tabel:
     * - videos,
     * - recommendation_candidates,
     * - feature_store_video,
     * - personalization_events.
     */
    private final JdbcTemplate jdbc;

    /**
     * Serwis eksperymentów A/B.
     *
     * Decyduje, który wariant algorytmu rankingu
     * ma zobaczyć konkretny użytkownik.
     */
    private final ExperimentService experiments;

    /**
     * Konfiguracja personalizacji.
     *
     * Używana m.in. do domyślnego limitu rekomendacji.
     */
    private final PersonalizationProperties properties;

    public RecommendationService(JdbcTemplate jdbc,
                                 ExperimentService experiments,
                                 PersonalizationProperties properties) {
        this.jdbc = jdbc;
        this.experiments = experiments;
        this.properties = properties;
    }

    /**
     * Zwraca rekomendacje filmów dla konkretnego użytkownika.
     *
     * Flow:
     * 1. Pobiera assignment A/B dla eksperymentu home_recommendations_ranking.
     * 2. Na podstawie wariantu wybiera algorytm rankingu.
     * 3. Pobiera publiczne i opublikowane filmy.
     * 4. Dołącza kandydatów rekomendacji i cechy filmów.
     * 5. Odrzuca filmy, które użytkownik już oglądał.
     * 6. Liczy rank_score zależnie od algorytmu.
     * 7. Sortuje wyniki malejąco po rank_score.
     * 8. Zwraca maksymalnie normalizedLimit(limit) wyników.
     *
     * @param userId użytkownik, dla którego liczymy rekomendacje
     * @param limit maksymalna liczba wyników oczekiwana przez klienta
     * @return lista rekomendacji z nazwą algorytmu i wariantem eksperymentu
     */
    public RecommendationResponse recommendations(UUID userId, int limit) {
        /*
         * Pobieramy stabilny wariant eksperymentu A/B.
         *
         * Ten sam użytkownik powinien dostawać ten sam wariant,
         * żeby wyniki eksperymentu były wiarygodne.
         */
        var assignment = experiments.assignment("home_recommendations_ranking", userId);

        /*
         * Wariant eksperymentu wybiera strategię rankingu.
         *
         * control/default:
         * - phase4_hybrid_v1
         *
         * trending_boost:
         * - mocniej promuje filmy aktualnie popularne.
         *
         * freshness_boost:
         * - mocniej promuje nowsze publikacje.
         */
        String algorithm = switch (assignment.variantKey()) {
            case "trending_boost" -> "phase4_trending_boost_v1";
            case "freshness_boost" -> "phase4_freshness_boost_v1";
            default -> "phase4_hybrid_v1";
        };

        /*
         * Zapytanie rankingowe.
         *
         * videos:
         * - bazowa lista publicznych filmów możliwych do pokazania.
         *
         * recommendation_candidates:
         * - wcześniej wygenerowani kandydaci per użytkownik.
         *
         * feature_store_video:
         * - cechy filmu, np. trending_score i quality_score.
         *
         * personalization_events:
         * - używane do odfiltrowania filmów już oglądanych przez użytkownika.
         */
        List<VideoRecommendation> items = jdbc.query("""
                select v.id as video_id, v.title, coalesce(c.reason, 'Dopasowane do aktywności') as reason,
                       case ?
                         when 'phase4_trending_boost_v1' then coalesce(c.score, 0) + coalesce(f.trending_score, 0) * 0.60
                         when 'phase4_freshness_boost_v1' then coalesce(c.score, 0) + extract(epoch from coalesce(v.published_at, v.created_at)) / 1000000000.0 * 0.45
                         else coalesce(c.score, 0) + coalesce(f.quality_score_7d, 0) * 0.35
                       end as rank_score
                from videos v
                left join recommendation_candidates c on c.video_id = v.id and c.user_id = ?
                left join feature_store_video f on f.video_id = v.id
                where v.status = 'PUBLISHED' and v.visibility = 'PUBLIC'
                  and not exists (
                    select 1 from personalization_events e
                    where e.user_id = ? and e.video_id = v.id and e.event_type in ('playback_start','view','playback_complete')
                  )
                order by rank_score desc, v.published_at desc nulls last
                limit ?
                """,
                (rs, rowNum) -> new VideoRecommendation(
                        rs.getObject("video_id", UUID.class),
                        rs.getString("title"),
                        rs.getString("reason"),
                        rs.getDouble("rank_score"),
                        algorithm,
                        assignment.variantKey()
                ),
                algorithm,
                userId,
                userId,
                normalizedLimit(limit)
        );

        return new RecommendationResponse(
                userId,
                algorithm,
                assignment.variantKey(),
                items
        );
    }

    /**
     * Zwraca ranking dla strony głównej.
     *
     * W tym MVP ranking homepage jest cienką nakładką na rekomendacje.
     * Dzięki temu homepage korzysta z tego samego eksperymentu A/B,
     * kandydatów i scoringu co endpoint rekomendacji.
     *
     * @param userId użytkownik, dla którego budujemy homepage
     * @param limit maksymalna liczba pozycji
     * @return ranking homepage
     */
    public RankingResponse homeRanking(UUID userId, int limit) {
        var response = recommendations(userId, limit);

        return new RankingResponse(
                "home",
                response.algorithm(),
                response.experimentVariant(),
                response.items()
        );
    }

    /**
     * Normalizuje limit wyników.
     *
     * Reguły:
     * - jeśli limit <= 0, używamy wartości domyślnej z konfiguracji,
     * - jeśli limit jest zbyt duży, obcinamy do 100.
     *
     * To chroni endpoint przed przypadkowym albo złośliwym requestem,
     * który próbowałby pobrać bardzo dużą liczbę rekomendacji naraz.
     */
    private int normalizedLimit(int limit) {
        if (limit <= 0) {
            return properties.defaultRecommendationLimit();
        }

        return Math.min(limit, 100);
    }
}