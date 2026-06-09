package com.example.newsfeed.experiment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Serwis odpowiedzialny za przypisywanie użytkowników do wariantów eksperymentów A/B.
 *
 * W kontekście News Feedu eksperymenty są używane głównie do testowania:
 * - różnych modeli rankingu,
 * - różnych wag w learning-to-rank,
 * - innych strategii mieszania feedu,
 * - rekomendacji,
 * - limitów diversity.
 *
 * Najważniejsza zasada:
 * użytkownik powinien dostać stabilny wariant eksperymentu.
 *
 * Czyli jeśli użytkownik raz trafił do wariantu ltr_v1,
 * kolejne requesty feedu powinny używać tego samego wariantu.
 */
@Service
public class ExperimentService {

    /**
     * Repozytorium definicji eksperymentów.
     *
     * Przechowuje konfigurację eksperymentu, np.:
     * - nazwę,
     * - status,
     * - procent ruchu,
     * - listę wariantów.
     */
    private final ExperimentRepository experimentRepository;

    /**
     * Repozytorium przypisań użytkowników do wariantów.
     *
     * To jest źródło stabilności eksperymentu.
     * Po pierwszym przypisaniu zapisujemy:
     * experimentName + userId -> variant.
     *
     * Dzięki temu użytkownik nie przeskakuje między wariantami
     * przy kolejnych requestach feedu.
     */
    private final ExperimentAssignmentRepository assignmentRepository;

    /**
     * Domyślny wariant rankingu.
     *
     * Używany, gdy:
     * - eksperyment nie istnieje,
     * - eksperyment nie jest uruchomiony,
     * - konfiguracja nie obejmuje użytkownika.
     */
    private final String defaultRankingVariant;

    /**
     * Wstrzyknięcie zależności i konfiguracji.
     *
     * defaultRankingVariant pochodzi z application.yml:
     *
     * newsfeed:
     *   experiment:
     *     default-ranking-variant: ltr_v1
     */
    public ExperimentService(
            ExperimentRepository experimentRepository,
            ExperimentAssignmentRepository assignmentRepository,
            @Value("${newsfeed.experiment.default-ranking-variant:ltr_v1}") String defaultRankingVariant
    ) {
        this.experimentRepository = experimentRepository;
        this.assignmentRepository = assignmentRepository;
        this.defaultRankingVariant = defaultRankingVariant;
    }

    /**
     * Zwraca wariant eksperymentu dla konkretnego użytkownika.
     *
     * To jest główna metoda używana np. przez FeedService.
     *
     * Flow:
     * 1. zbuduj klucz assignmentu: experimentName + userId,
     * 2. sprawdź, czy użytkownik ma już przypisany wariant,
     * 3. jeśli tak — zwróć istniejący wariant,
     * 4. jeśli nie — wybierz wariant deterministycznie,
     * 5. zapisz assignment w bazie,
     * 6. zwróć wariant.
     *
     * Metoda jest transakcyjna, bo ewentualnie zapisuje nowe przypisanie.
     */
    @Transactional
    public String assignVariant(UUID userId, String experimentName) {
        /*
         * Klucz przypisania użytkownika do eksperymentu.
         *
         * Jeden użytkownik może mieć osobny wariant dla każdego eksperymentu.
         */
        ExperimentAssignmentId id = new ExperimentAssignmentId(
                experimentName,
                userId
        );

        /*
         * Jeśli assignment już istnieje, zwracamy zapisany wariant.
         *
         * To jest kluczowe dla stabilności eksperymentów.
         * Bez tego użytkownik mógłby raz zobaczyć feed z ltr_v1,
         * a za chwilę z ltr_v2.
         */
        return assignmentRepository.findById(id)
                .map(ExperimentAssignment::getVariant)
                .orElseGet(() -> {
                    /*
                     * Brak istniejącego assignmentu.
                     *
                     * Wybieramy wariant i zapisujemy go,
                     * żeby kolejne requesty zwracały dokładnie ten sam wariant.
                     */
                    String variant = chooseVariant(userId, experimentName);

                    assignmentRepository.save(
                            new ExperimentAssignment(
                                    experimentName,
                                    userId,
                                    variant,
                                    Instant.now()
                            )
                    );

                    return variant;
                });
    }

    /**
     * Wybiera wariant eksperymentu dla użytkownika.
     *
     * Ta metoda nie zapisuje nic do bazy.
     * Tylko wylicza, jaki wariant powinien dostać użytkownik.
     *
     * Aktualna implementacja:
     * - jeśli eksperyment nie istnieje albo nie jest running,
     *   zwraca defaultRankingVariant,
     * - wylicza bucket 0-99 na podstawie userId + experimentName,
     * - jeśli bucket nie mieści się w trafficPercentage,
     *   użytkownik trafia do control,
     * - jeśli mieści się w eksperymencie,
     *   rozdziela ruch między ltr_v1 i ltr_v2.
     */
    private String chooseVariant(UUID userId, String experimentName) {
        /*
         * Pobieramy konfigurację eksperymentu po nazwie.
         *
         * Przykład:
         * experimentName = "feed-ranking"
         */
        Optional<Experiment> experiment = experimentRepository.findByName(
                experimentName
        );

        /*
         * Jeśli eksperyment nie istnieje albo nie jest aktywny,
         * nie eksperymentujemy — używamy wariantu domyślnego.
         *
         * To jest bezpieczne zachowanie produkcyjne:
         * brak konfiguracji nie powinien psuć feedu.
         */
        if (experiment.isEmpty()
                || !"running".equalsIgnoreCase(experiment.get().getStatus())) {
            return defaultRankingVariant;
        }

        /*
         * Stabilny bucket użytkownika.
         *
         * Objects.hash(userId, experimentName) daje deterministyczną wartość
         * dla tej pary użytkownik + eksperyment.
         *
         * floorMod(..., 100) mapuje wynik do zakresu 0-99.
         *
         * Dzięki temu użytkownik zawsze wpada do tego samego bucketa.
         */
        int bucket = Math.floorMod(
                Objects.hash(userId, experimentName),
                100
        );

        /*
         * trafficPercentage określa, jaki procent użytkowników
         * bierze udział w eksperymencie.
         *
         * Przykład:
         * trafficPercentage = 20
         *
         * Bucket 0-19 bierze udział w eksperymencie.
         * Bucket 20-99 trafia do control.
         */
        if (bucket >= experiment.get().getTrafficPercentage()) {
            return "control";
        }

        /*
         * Prosty podział ruchu między dwa warianty.
         *
         * Użytkownicy z parzystym bucketem trafiają do ltr_v1,
         * z nieparzystym do ltr_v2.
         *
         * W produkcyjnej wersji warianty i ich wagi powinny pochodzić
         * z variantsJson, a nie być wpisane na sztywno.
         */
        return bucket % 2 == 0
                ? "ltr_v1"
                : "ltr_v2";
    }
}