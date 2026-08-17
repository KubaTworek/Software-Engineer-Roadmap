package com.example.videostreaming.personalization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.example.videostreaming.personalization.PersonalizationDtos.*;

/**
 * Serwis odpowiedzialny za lokalny feature store i proste agregacje personalizacji.
 *
 * Główna odpowiedzialność:
 * - przelicza dzienne metryki video do lokalnego warehouse,
 * - przelicza cechy użytkowników,
 * - przelicza cechy filmów,
 * - generuje kandydatów rekomendacji,
 * - udostępnia podgląd feature store dla admina/analityki.
 *
 * Ważne:
 * To jest MVP feature store oparte o PostgreSQL.
 * Produkcyjnie ta logika zwykle trafia do osobnego pipeline'u:
 * event stream -> data lake/warehouse -> batch jobs -> online feature store.
 */
@Service
public class FeatureStoreService {

    /**
     * Bezpośredni dostęp do SQL.
     *
     * Tutaj JdbcTemplate jest sensowny, bo większość pracy to agregacje,
     * INSERT ... SELECT, UPSERT-y i zapytania analityczne.
     *
     * JPA byłoby mniej wygodne dla tego typu operacji.
     */
    private final JdbcTemplate jdbc;

    public FeatureStoreService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Przelicza cały lokalny pipeline feature store.
     *
     * Kolejność ma znaczenie:
     * 1. Najpierw budujemy dzienne metryki warehouse.
     * 2. Potem przeliczamy cechy użytkowników.
     * 3. Potem przeliczamy cechy filmów.
     * 4. Na końcu generujemy kandydatów rekomendacji.
     *
     * Całość działa w jednej transakcji.
     * Jeśli któryś etap się wywróci, zmiany z poprzednich etapów zostaną cofnięte.
     *
     * @return podsumowanie liczby przeliczonych rekordów
     */
    @Transactional
    public FeatureRecomputeResponse recompute() {
        int warehouseRows = recomputeWarehouseDailyMetrics();
        int userRows = recomputeUserFeatures();
        int videoRows = recomputeVideoFeatures();

        recomputeRecommendationCandidates();

        return new FeatureRecomputeResponse(
                "ok",
                userRows,
                videoRows,
                warehouseRows
        );
    }

    /**
     * Przelicza dzienne metryki video za ostatnie 31 dni.
     *
     * Źródło danych:
     * - personalization_events.
     *
     * Tabela docelowa:
     * - warehouse_daily_video_metrics.
     *
     * Liczone metryki:
     * - views,
     * - starts,
     * - completions,
     * - unique_users,
     * - avg_startup_ms,
     * - rebuffer_ratio.
     *
     * To jest lokalny odpowiednik data warehouse.
     * Z tych agregatów korzysta później feature_store_video.
     */
    public int recomputeWarehouseDailyMetrics() {
        /*
         * Usuwamy ostatnie 31 dni, bo ten okres przeliczamy od nowa.
         *
         * To proste podejście batchowe:
         * zamiast próbować aktualizować pojedyncze eventy inkrementalnie,
         * przeliczamy świeży zakres danych.
         */
        jdbc.update("delete from warehouse_daily_video_metrics where metric_date >= current_date - interval '31 days'");

        return jdbc.update("""
                insert into warehouse_daily_video_metrics
                (metric_date, video_id, views, starts, completions, unique_users, avg_startup_ms, rebuffer_ratio, updated_at)
                select
                  date(occurred_at) as metric_date,
                  video_id,
                  count(*) filter (where event_type in ('playback_start','view')) as views,
                  count(*) filter (where event_type = 'playback_start') as starts,
                  count(*) filter (where event_type = 'playback_complete') as completions,
                  count(distinct user_id) as unique_users,
                  coalesce(avg((attributes_json ->> 'startupTimeMs')::numeric) filter (where attributes_json ? 'startupTimeMs'), 0) as avg_startup_ms,
                  coalesce(sum((attributes_json ->> 'rebufferTimeMs')::numeric) filter (where attributes_json ? 'rebufferTimeMs') / greatest(count(*), 1), 0) as rebuffer_ratio,
                  now()
                from personalization_events
                where video_id is not null and occurred_at >= current_date - interval '31 days'
                group by date(occurred_at), video_id
                on conflict (metric_date, video_id) do update set
                  views = excluded.views,
                  starts = excluded.starts,
                  completions = excluded.completions,
                  unique_users = excluded.unique_users,
                  avg_startup_ms = excluded.avg_startup_ms,
                  rebuffer_ratio = excluded.rebuffer_ratio,
                  updated_at = now()
                """);
    }

    /**
     * Przelicza cechy użytkowników na podstawie eventów z ostatnich 30 dni.
     *
     * Tabela docelowa:
     * - feature_store_user.
     *
     * Liczone cechy:
     * - favorite_category,
     * - watched_videos_30d,
     * - completed_videos_30d,
     * - avg_completion_rate.
     *
     * Te cechy mogą być używane później do rekomendacji,
     * segmentacji użytkowników albo rankingu homepage.
     */
    public int recomputeUserFeatures() {
        /*
         * Czyścimy bardzo stare rekordy feature store.
         *
         * Jeśli użytkownik nie generował eventów od ponad 6 miesięcy,
         * jego cechy mogą być nieaktualne i niepotrzebne operacyjnie.
         */
        jdbc.update("delete from feature_store_user where updated_at < now() - interval '6 months'");

        return jdbc.update("""
                insert into feature_store_user
                (user_id, favorite_category, watched_videos_30d, completed_videos_30d, avg_completion_rate, updated_at)
                select
                  user_id,
                  coalesce(mode() within group (order by source), 'general') as favorite_category,
                  count(distinct video_id) filter (where event_type in ('playback_start','view')) as watched_videos_30d,
                  count(distinct video_id) filter (where event_type = 'playback_complete') as completed_videos_30d,
                  case when count(*) filter (where event_type in ('playback_start','view')) = 0 then 0
                       else count(*) filter (where event_type = 'playback_complete')::numeric / count(*) filter (where event_type in ('playback_start','view')) end as avg_completion_rate,
                  now()
                from personalization_events
                where user_id is not null and occurred_at >= now() - interval '30 days'
                group by user_id
                on conflict (user_id) do update set
                  favorite_category = excluded.favorite_category,
                  watched_videos_30d = excluded.watched_videos_30d,
                  completed_videos_30d = excluded.completed_videos_30d,
                  avg_completion_rate = excluded.avg_completion_rate,
                  updated_at = now()
                """);
    }

    /**
     * Przelicza cechy filmów na podstawie dziennych metryk warehouse.
     *
     * Tabela docelowa:
     * - feature_store_video.
     *
     * Liczone cechy:
     * - views_7d,
     * - views_30d,
     * - completion_rate_7d,
     * - quality_score_7d,
     * - trending_score.
     *
     * Uwzględniane są tylko filmy:
     * - PUBLISHED,
     * - PUBLIC.
     *
     * Dzięki temu rekomendacje i ranking nie promują szkiców,
     * filmów prywatnych ani materiałów jeszcze nieopublikowanych.
     */
    public int recomputeVideoFeatures() {
        return jdbc.update("""
                insert into feature_store_video
                (video_id, views_7d, views_30d, completion_rate_7d, quality_score_7d, trending_score, updated_at)
                select
                  v.id,
                  coalesce(sum(m.views) filter (where m.metric_date >= current_date - interval '7 days'), 0),
                  coalesce(sum(m.views), 0),
                  case when coalesce(sum(m.starts) filter (where m.metric_date >= current_date - interval '7 days'), 0) = 0 then 0
                       else coalesce(sum(m.completions) filter (where m.metric_date >= current_date - interval '7 days'), 0)::numeric /
                            greatest(coalesce(sum(m.starts) filter (where m.metric_date >= current_date - interval '7 days'), 0), 1) end,
                  greatest(0, 100 - coalesce(avg(m.avg_startup_ms) filter (where m.metric_date >= current_date - interval '7 days'), 0) / 100 - coalesce(avg(m.rebuffer_ratio) filter (where m.metric_date >= current_date - interval '7 days'), 0)),
                  coalesce(sum(m.views) filter (where m.metric_date >= current_date - interval '2 days'), 0) * 1.0
                    + coalesce(sum(m.unique_users) filter (where m.metric_date >= current_date - interval '2 days'), 0) * 3.0
                    + coalesce(sum(m.completions) filter (where m.metric_date >= current_date - interval '2 days'), 0) * 2.0,
                  now()
                from videos v
                left join warehouse_daily_video_metrics m on m.video_id = v.id and m.metric_date >= current_date - interval '30 days'
                where v.status = 'PUBLISHED' and v.visibility = 'PUBLIC'
                group by v.id
                on conflict (video_id) do update set
                  views_7d = excluded.views_7d,
                  views_30d = excluded.views_30d,
                  completion_rate_7d = excluded.completion_rate_7d,
                  quality_score_7d = excluded.quality_score_7d,
                  trending_score = excluded.trending_score,
                  updated_at = now()
                """);
    }

    /**
     * Generuje kandydatów rekomendacji dla użytkowników.
     *
     * Tabela docelowa:
     * - recommendation_candidates.
     *
     * Logika MVP:
     * - bierzemy wszystkich użytkowników,
     * - bierzemy publiczne i opublikowane filmy,
     * - odrzucamy filmy, które użytkownik już oglądał,
     * - liczymy prosty score na podstawie trendu, jakości i świeżości publikacji.
     *
     * To nie jest jeszcze pełny system rekomendacji ML.
     * To prosty candidate generation, który można później ulepszyć rankingiem ML.
     */
    public void recomputeRecommendationCandidates() {
        /*
         * Czyścimy poprzednie kandydatury.
         *
         * MVP generuje kandydatów od zera.
         * Produkcyjnie można byłoby robić to przyrostowo albo per segment użytkowników.
         */
        jdbc.update("delete from recommendation_candidates");

        jdbc.update("""
                insert into recommendation_candidates (id, user_id, video_id, algorithm, reason, score, generated_at)
                select uuid_generate_v4(), u.id, v.id, 'phase4_hybrid_v1',
                       case when f.trending_score > 0 then 'Popularne teraz' else 'Nowość w katalogu' end,
                       coalesce(f.trending_score, 0) * 0.55 + coalesce(f.quality_score_7d, 0) * 0.25
                         + extract(epoch from coalesce(v.published_at, v.created_at)) / 1000000000.0 * 0.20,
                       now()
                from users u
                join videos v on v.status = 'PUBLISHED' and v.visibility = 'PUBLIC'
                left join feature_store_video f on f.video_id = v.id
                where not exists (
                    select 1 from personalization_events e
                    where e.user_id = u.id and e.video_id = v.id and e.event_type in ('playback_start','view','playback_complete')
                )
                """);
    }

    /**
     * Zwraca cechy konkretnego użytkownika z feature store.
     *
     * Używane przez admin endpoint do diagnostyki personalizacji.
     *
     * Jeśli rekord nie istnieje, queryForObject rzuci wyjątek.
     * W produkcyjnym API warto zamienić to na czytelne 404.
     */
    public UserFeatureResponse userFeature(UUID userId) {
        return jdbc.queryForObject("""
                select user_id, favorite_category, watched_videos_30d, completed_videos_30d, avg_completion_rate, updated_at
                from feature_store_user where user_id = ?
                """,
                (rs, rowNum) -> new UserFeatureResponse(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("favorite_category"),
                        rs.getInt("watched_videos_30d"),
                        rs.getInt("completed_videos_30d"),
                        rs.getDouble("avg_completion_rate"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                userId
        );
    }

    /**
     * Zwraca cechy konkretnego filmu z feature store.
     *
     * Używane do diagnostyki rankingu i rekomendacji.
     *
     * Przykładowo pozwala sprawdzić, czy film ma wysoki trending_score,
     * niski quality_score albo niski completion_rate.
     */
    public VideoFeatureResponse videoFeature(UUID videoId) {
        return jdbc.queryForObject("""
                select video_id, views_7d, views_30d, completion_rate_7d, quality_score_7d, trending_score, updated_at
                from feature_store_video where video_id = ?
                """,
                (rs, rowNum) -> new VideoFeatureResponse(
                        rs.getObject("video_id", UUID.class),
                        rs.getLong("views_7d"),
                        rs.getLong("views_30d"),
                        rs.getDouble("completion_rate_7d"),
                        rs.getDouble("quality_score_7d"),
                        rs.getDouble("trending_score"),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                videoId
        );
    }

    /**
     * Zwraca dzienne metryki video z lokalnego warehouse.
     *
     * Używane przez admin/analitykę do podglądu danych wejściowych,
     * na których bazują feature_store_video, trending i rekomendacje.
     *
     * Parametry:
     * - days: ile ostatnich dni pokazać,
     * - limit: maksymalna liczba rekordów.
     */
    public List<DailyVideoMetric> dailyMetrics(int days, int limit) {
        return jdbc.query("""
                select metric_date, video_id, views, starts, completions, unique_users, avg_startup_ms, rebuffer_ratio
                from warehouse_daily_video_metrics
                where metric_date >= current_date - (? * interval '1 day')
                order by metric_date desc, views desc
                limit ?
                """,
                (rs, rowNum) -> new DailyVideoMetric(
                        rs.getDate("metric_date").toLocalDate(),
                        rs.getObject("video_id", UUID.class),
                        rs.getLong("views"),
                        rs.getLong("starts"),
                        rs.getLong("completions"),
                        rs.getLong("unique_users"),
                        rs.getDouble("avg_startup_ms"),
                        rs.getDouble("rebuffer_ratio")
                ),
                days,
                limit
        );
    }
}