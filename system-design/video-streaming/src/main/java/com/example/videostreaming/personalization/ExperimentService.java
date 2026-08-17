package com.example.videostreaming.personalization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za przypisywanie użytkowników do wariantów eksperymentów A/B.
 *
 * Główna odpowiedzialność:
 * - sprawdza, czy użytkownik ma już assignment dla eksperymentu,
 * - jeśli nie ma, wybiera wariant na podstawie stabilnego bucketingu,
 * - zapisuje assignment w bazie,
 * - zapewnia, że ten sam użytkownik dostaje ten sam wariant.
 *
 * Ważne:
 * Assignment powinien być stabilny.
 * Użytkownik nie powinien raz trafić do control, a potem do treatment,
 * bo zepsułoby to wyniki eksperymentu i doświadczenie użytkownika.
 */
@Service
public class ExperimentService {

    /**
     * JdbcTemplate jest używany bezpośrednio, bo logika eksperymentów
     * opiera się na prostych zapytaniach do kilku tabel:
     * - ab_experiments,
     * - ab_experiment_variants,
     * - ab_assignments.
     *
     * W tym miejscu nie potrzebujemy rozbudowanego repozytorium JPA.
     */
    private final JdbcTemplate jdbc;

    public ExperimentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Zwraca wariant eksperymentu przypisany do użytkownika.
     *
     * Flow:
     * 1. Sprawdza, czy istnieje już assignment dla experimentKey + userId.
     * 2. Jeśli istnieje, zwraca go jako stabilne przypisanie.
     * 3. Jeśli nie istnieje, pobiera warianty aktywnego eksperymentu.
     * 4. Wybiera wariant przez deterministic bucketing.
     * 5. Próbuje zapisać assignment w bazie.
     * 6. Zwraca wybrany wariant.
     *
     * Klucz:
     * Assignment jest per użytkownik i per eksperyment.
     * Ten sam użytkownik może być w różnych wariantach różnych eksperymentów.
     *
     * @param experimentKey techniczny klucz eksperymentu, np. home_ranking_v2
     * @param userId użytkownik, dla którego wybieramy wariant
     * @return assignment użytkownika do wariantu
     */
    public Assignment assignment(String experimentKey, UUID userId) {
        /*
         * Najpierw sprawdzamy, czy assignment już istnieje.
         *
         * To jest podstawowa gwarancja stabilności eksperymentu.
         * Jeśli użytkownik został już przypisany do wariantu,
         * nie przeliczamy go ponownie.
         */
        List<Assignment> existing = jdbc.query("""
                select experiment_key, variant_key, assigned_at from ab_assignments
                where experiment_key = ? and user_id = ?
                """,
                (rs, rowNum) -> new Assignment(
                        rs.getString("experiment_key"),
                        rs.getString("variant_key"),
                        false
                ),
                experimentKey,
                userId
        );

        if (!existing.isEmpty()) {
            return existing.getFirst();
        }

        /*
         * Pobieramy warianty tylko dla eksperymentu w statusie RUNNING.
         *
         * traffic_percent określa, jaki procent użytkowników ma trafić
         * do danego wariantu.
         *
         * Przykład:
         * control = 50
         * treatment = 50
         */
        List<Variant> variants = jdbc.query("""
                select variant_key, traffic_percent from ab_experiment_variants v
                join ab_experiments e on e.id = v.experiment_id
                where e.experiment_key = ? and e.status = 'RUNNING'
                order by v.variant_key
                """,
                (rs, rowNum) -> new Variant(
                        rs.getString("variant_key"),
                        rs.getInt("traffic_percent")
                ),
                experimentKey
        );

        /*
         * Wybieramy wariant deterministycznie.
         *
         * To nie jest losowanie runtime.
         * Bucket zależy od experimentKey + userId, więc dla tego samego użytkownika
         * i eksperymentu wynik będzie taki sam.
         */
        String variant = chooseVariant(experimentKey, userId, variants);

        /*
         * Zapisujemy assignment.
         *
         * on conflict zabezpiecza przed race condition:
         * jeśli dwa requesty równolegle próbują przypisać tego samego usera,
         * tylko jeden insert wygra.
         *
         * Uwaga:
         * Obecna metoda po konflikcie nadal zwraca lokalnie wybrany wariant.
         * W praktyce, ponieważ chooseVariant jest deterministyczny,
         * powinien być taki sam jak zapisany wariant.
         */
        jdbc.update("""
                insert into ab_assignments (id, experiment_key, user_id, variant_key, assigned_at)
                values (?, ?, ?, ?, ?)
                on conflict (experiment_key, user_id) do nothing
                """,
                UUID.randomUUID(),
                experimentKey,
                userId,
                variant,
                Instant.now()
        );

        return new Assignment(experimentKey, variant, true);
    }

    /**
     * Wybiera wariant eksperymentu na podstawie bucketu 0–99.
     *
     * Flow:
     * 1. Jeśli eksperyment nie ma aktywnych wariantów, zwraca "control".
     * 2. Liczy stabilny bucket dla experimentKey + userId.
     * 3. Przechodzi po wariantach i sumuje traffic_percent.
     * 4. Zwraca pierwszy wariant, którego zakres obejmuje bucket.
     *
     * Przykład:
     * bucket = 37
     * variants:
     * - control 50%
     * - treatment 50%
     *
     * cumulative po control = 50, więc bucket 37 trafia do control.
     *
     * @param experimentKey klucz eksperymentu
     * @param userId użytkownik
     * @param variants lista wariantów i procentów ruchu
     * @return wybrany variantKey
     */
    private String chooseVariant(String experimentKey, UUID userId, List<Variant> variants) {
        if (variants.isEmpty()) {
            return "control";
        }

        int bucket = stableBucket(experimentKey + ":" + userId);

        int cumulative = 0;

        for (Variant variant : variants) {
            cumulative += variant.trafficPercent();

            if (bucket < cumulative) {
                return variant.variantKey();
            }
        }

        /*
         * Fallback na ostatni wariant.
         *
         * Chroni przed niedoskonałą konfiguracją procentów,
         * np. gdy suma traffic_percent jest mniejsza niż 100.
         */
        return variants.getLast().variantKey();
    }

    /**
     * Liczy stabilny bucket 0–99 dla podanej wartości.
     *
     * Używamy SHA-256, żeby rozkład użytkowników był stabilny i równomierny.
     *
     * Bierzemy pierwsze 4 bajty hasha, interpretujemy jako liczbę unsigned
     * i robimy modulo 100.
     *
     * Dzięki temu:
     * - ten sam userId + experimentKey zawsze daje ten sam bucket,
     * - różne eksperymenty mogą dać temu samemu użytkownikowi inne buckety,
     * - rozkład jest wystarczająco równy dla MVP A/B testów.
     */
    private int stableBucket(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            String firstBytes = HexFormat.of().formatHex(hash, 0, 4);

            long unsigned = Long.parseUnsignedLong(firstBytes, 16);

            return (int) (unsigned % 100);
        } catch (Exception e) {
            /*
             * Awaryjny fallback.
             *
             * Nie powinien się wydarzyć, bo SHA-256 jest standardowym algorytmem.
             * Jeśli jednak coś pójdzie źle, nadal zwracamy stabilny bucket
             * oparty o hashCode.
             */
            return Math.abs(value.hashCode() % 100);
        }
    }

    /**
     * Wynik przypisania użytkownika do eksperymentu.
     *
     * newlyAssigned:
     * - true, jeśli assignment został utworzony w tym wywołaniu,
     * - false, jeśli użytkownik miał już wcześniejsze przypisanie.
     */
    public record Assignment(
            String experimentKey,
            String variantKey,
            boolean newlyAssigned
    ) {}

    /**
     * Wewnętrzny model wariantu eksperymentu.
     *
     * variantKey:
     * - nazwa wariantu, np. control, treatment_a.
     *
     * trafficPercent:
     * - procent ruchu kierowany do wariantu.
     */
    private record Variant(
            String variantKey,
            int trafficPercent
    ) {}
}