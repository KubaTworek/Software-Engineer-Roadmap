package com.example.newsfeed.feature;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Serwis dostępu do Feature Store.
 *
 * Feature Store przechowuje cechy używane przez:
 * - ranking feedu,
 * - recommendation service,
 * - learning-to-rank,
 * - anti-spam,
 * - moderację,
 * - analitykę.
 *
 * W tej wersji Feature Store jest prosty i oparty o bazę danych.
 * Produkcyjnie można go podzielić na:
 * - Online Feature Store, np. Redis / DynamoDB / Cassandra,
 * - Offline Feature Store, np. S3 / BigQuery / Snowflake / Databricks.
 *
 * Ta klasa nie liczy feature’ów.
 * Ona je zapisuje i odczytuje dla innych komponentów systemu.
 */
@Service
public class FeatureStoreService {

    /**
     * Repozytorium feature’ów użytkownika.
     *
     * Przechowuje cechy typu:
     * - zainteresowania tematami,
     * - affinity do autorów,
     * - średni czas sesji,
     * - preferencje wynikające z historii kliknięć.
     */
    private final UserFeatureRepository userFeatureRepository;

    /**
     * Repozytorium feature’ów posta.
     *
     * Przechowuje cechy typu:
     * - qualityScore,
     * - spamScore,
     * - CTR,
     * - reportRate,
     * - engagement velocity.
     *
     * Te wartości są potem używane przez LearningToRankService.
     */
    private final PostFeatureRepository postFeatureRepository;

    /**
     * Wstrzyknięcie repozytoriów Feature Store.
     */
    public FeatureStoreService(
            UserFeatureRepository userFeatureRepository,
            PostFeatureRepository postFeatureRepository
    ) {
        this.userFeatureRepository = userFeatureRepository;
        this.postFeatureRepository = postFeatureRepository;
    }

    /**
     * Pobiera feature’y użytkownika.
     *
     * Używane np. przez:
     * - recommendation service,
     * - ranking,
     * - personalizację feedu.
     *
     * Optional.empty() oznacza cold start:
     * użytkownik nie ma jeszcze wyliczonych feature’ów.
     */
    @Transactional(readOnly = true)
    public Optional<UserFeature> getUserFeatures(UUID userId) {
        /*
         * userId jest kluczem głównym w tabeli user_features.
         *
         * Jeśli rekord istnieje, dostajemy aktualny snapshot cech użytkownika.
         */
        return userFeatureRepository.findById(userId);
    }

    /**
     * Pobiera feature’y wielu postów naraz.
     *
     * To jest ważne wydajnościowo.
     *
     * Feed zwykle rankinguje wiele kandydatów jednocześnie.
     * Zamiast robić osobne zapytanie dla każdego posta,
     * pobieramy feature’y batchowo przez IN (:postIds).
     *
     * Wynik zwracamy jako Map:
     * postId -> PostFeature.
     *
     * Dzięki temu ranking może szybko znaleźć feature’y konkretnego posta.
     */
    @Transactional(readOnly = true)
    public Map<UUID, PostFeature> getPostFeatures(Collection<UUID> postIds) {
        /*
         * Jeśli lista jest pusta, nie odpytujemy bazy.
         */
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PostFeature> result = new HashMap<>();

        /*
         * Batch read feature’ów postów.
         *
         * Repository powinno wykonać jedno zapytanie:
         * WHERE post_id IN (...)
         */
        for (PostFeature feature : postFeatureRepository.findByPostIdIn(postIds)) {
            /*
             * Mapujemy wynik po postId.
             *
             * To upraszcza użycie w LearningToRankService:
             * features.get(post.getId()).
             */
            result.put(feature.getPostId(), feature);
        }

        return result;
    }

    /**
     * Tworzy domyślne feature’y dla nowego posta.
     *
     * Wywoływane po utworzeniu posta.
     *
     * Na początku post nie ma jeszcze danych o kliknięciach,
     * komentarzach, CTR ani reportach, więc dostaje neutralne wartości.
     *
     * Później pipeline analityczny może aktualizować te feature’y
     * na podstawie realnego zachowania użytkowników.
     */
    @Transactional
    public void upsertDefaultPostFeatures(UUID postId) {
        /*
         * qualityScore = 0.5
         *
         * Neutralna jakość posta.
         * Nie promujemy go ani nie karzemy na starcie.
         */
        double defaultQualityScore = 0.5;

        /*
         * spamScore = 0.0
         *
         * Domyślnie zakładamy brak sygnałów spamowych.
         * Jeśli moderacja albo anti-abuse wykryje ryzyko,
         * ten score może zostać podniesiony.
         */
        double defaultSpamScore = 0.0;

        /*
         * ctr1h / ctr24h = 0
         *
         * Nowy post nie ma jeszcze historii kliknięć.
         */
        double defaultCtr1h = 0.0;
        double defaultCtr24h = 0.0;

        /*
         * reportRate = 0
         *
         * Nowy post nie ma jeszcze zgłoszeń.
         */
        double defaultReportRate = 0.0;

        /*
         * save działa tutaj jak upsert na poziomie JPA:
         * - jeśli rekord nie istnieje, zostanie utworzony,
         * - jeśli istnieje, zostanie nadpisany.
         *
         * Uwaga: jeśli później pipeline aktualizuje feature’y,
         * trzeba uważać, żeby nie nadpisać wartości produkcyjnych
         * domyślnymi przy ponownym wywołaniu tej metody.
         */
        postFeatureRepository.save(
                new PostFeature(
                        postId,
                        defaultQualityScore,
                        defaultSpamScore,
                        defaultCtr1h,
                        defaultCtr24h,
                        defaultReportRate,
                        Instant.now()
                )
        );
    }
}