package com.example.newsfeed.celebrity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serwis obsługujący tzw. celebrity pull model.
 *
 * Problem:
 * zwykły fan-out on write działa dobrze dla normalnych autorów,
 * ale nie skaluje się dla autorów z ogromną liczbą followersów.
 *
 * Przykład:
 * jeśli autor ma 10 mln obserwujących, to jeden jego post oznaczałby
 * 10 mln wpisów do feed_inbox.
 *
 * Rozwiązanie:
 * dla bardzo popularnych autorów nie robimy pełnego fan-outu.
 * Zamiast tego zapisujemy ich posty osobno i dociągamy je podczas odczytu feedu.
 *
 * Czyli:
 * - zwykły autor: push do feed_inbox followersów,
 * - celebrity author: pull podczas budowania feedu.
 */
@Service
public class CelebrityService {

    /**
     * Repozytorium autorów oznaczonych jako celebrity.
     *
     * Jeśli authorId znajduje się w tej tabeli,
     * system traktuje autora jako zbyt dużego do klasycznego fan-outu.
     */
    private final CelebrityAuthorRepository celebrityAuthorRepository;

    /**
     * Repozytorium postów autorów-celebrytów.
     *
     * Te posty nie muszą trafiać do feed_inbox każdego obserwującego.
     * Są pobierane dynamicznie podczas budowania feedu użytkownika.
     */
    private final CelebrityPostRepository celebrityPostRepository;

    /**
     * Próg liczby followersów, od którego autor zostaje uznany za celebrity.
     *
     * Wartość pochodzi z konfiguracji:
     *
     * newsfeed:
     *   fanout:
     *     celebrity-threshold: 100000
     *
     * Dzięki temu można dostroić zachowanie bez zmiany kodu.
     */
    private final long threshold;

    /**
     * Wstrzyknięcie repozytoriów i progu celebrity z konfiguracji.
     */
    public CelebrityService(
            CelebrityAuthorRepository celebrityAuthorRepository,
            CelebrityPostRepository celebrityPostRepository,
            @Value("${newsfeed.fanout.celebrity-threshold:100000}") long threshold
    ) {
        this.celebrityAuthorRepository = celebrityAuthorRepository;
        this.celebrityPostRepository = celebrityPostRepository;
        this.threshold = threshold;
    }

    /**
     * Aktualizuje status autora jako celebrity.
     *
     * Metoda wywoływana jest zwykle w fan-out workerze,
     * gdy system zna aktualną liczbę followersów autora.
     *
     * Jeśli liczba followersów przekracza threshold,
     * a autor nie jest jeszcze oznaczony jako celebrity,
     * zapisujemy go w tabeli celebrity_authors.
     *
     * Od tego momentu jego nowe posty nie powinny być masowo fan-outowane
     * do feed_inbox każdego obserwującego.
     */
    @Transactional
    public void refreshCelebrityStatus(UUID authorId, long followerCount) {
        /*
         * Sprawdzamy, czy autor przekroczył próg popularności.
         *
         * Drugi warunek chroni przed wielokrotnym zapisem tego samego autora.
         */
        if (followerCount >= threshold && !celebrityAuthorRepository.existsById(authorId)) {
            Instant now = Instant.now();

            /*
             * Zapisujemy autora jako celebrity.
             *
             * followerCount zapisujemy jako snapshot,
             * czyli informację, z jaką liczbą followersów autor został oznaczony.
             */
            celebrityAuthorRepository.save(
                    new CelebrityAuthor(
                            authorId,
                            followerCount,
                            now,
                            now
                    )
            );
        }
    }

    /**
     * Sprawdza, czy autor jest oznaczony jako celebrity.
     *
     * Ta metoda jest krytyczna dla decyzji fan-out workerów.
     *
     * Jeśli true:
     * - nie robimy klasycznego fan-outu do wszystkich followersów,
     * - zapisujemy post do celebrity_posts.
     *
     * Jeśli false:
     * - wykonujemy normalny fan-out on write.
     */
    @Transactional(readOnly = true)
    public boolean isCelebrity(UUID authorId) {
        return celebrityAuthorRepository.existsById(authorId);
    }

    /**
     * Dodaje post autora-celebryty do osobnej tabeli celebrity_posts.
     *
     * Ta tabela jest później używana podczas odczytu feedu.
     *
     * Zamiast zapisywać post do milionów feed_inboxów,
     * zapisujemy jeden rekord:
     * authorId + postId + createdAt.
     */
    @Transactional
    public void addCelebrityPost(UUID authorId, UUID postId, Instant createdAt) {
        celebrityPostRepository.save(
                new CelebrityPost(
                        authorId,
                        postId,
                        createdAt
                )
        );
    }

    /**
     * Usuwa post celebryty z indeksu celebrity_posts.
     *
     * Wywoływane przy usunięciu posta.
     *
     * Dzięki temu usunięty post nie będzie dociągany
     * podczas budowania feedu użytkowników.
     */
    @Transactional
    public void removeCelebrityPost(UUID postId) {
        celebrityPostRepository.deleteByPostId(postId);
    }

    /**
     * Pobiera najnowsze posty celebrytów, których obserwuje użytkownik.
     *
     * Ta metoda jest używana przez FeedService podczas budowania feedu.
     *
     * Flow:
     * 1. FeedService pobiera listę obserwowanych autorów użytkownika,
     * 2. przekazuje ją tutaj,
     * 3. CelebrityService pobiera najnowsze posty tych autorów z celebrity_posts,
     * 4. FeedService miesza je z feed_inbox i rekomendacjami.
     *
     * To jest sedno celebrity pull model.
     */
    @Transactional(readOnly = true)
    public Set<UUID> getRecentCelebrityPostIds(Collection<UUID> followedCelebrityAuthorIds, int limit) {
        /*
         * Jeśli użytkownik nikogo nie obserwuje albo lista jest pusta,
         * nie ma sensu odpytywać bazy.
         */
        if (followedCelebrityAuthorIds == null || followedCelebrityAuthorIds.isEmpty()) {
            return Set.of();
        }

        /*
         * Pobieramy najnowsze posty obserwowanych celebrytów.
         *
         * PageRequest ogranicza liczbę wyników, żeby jeden użytkownik
         * obserwujący wielu popularnych autorów nie wygenerował zbyt dużego query.
         */
        return celebrityPostRepository
                .findByAuthorIdInOrderByCreatedAtDesc(
                        followedCelebrityAuthorIds,
                        PageRequest.of(0, limit)
                )
                .stream()

                /*
                 * FeedService potrzebuje tylko ID postów.
                 *
                 * Pełne encje Post zostaną później dociągnięte z PostRepository
                 * razem z innymi kandydatami feedu.
                 */
                .map(CelebrityPost::getPostId)

                /*
                 * Set usuwa ewentualne duplikaty.
                 */
                .collect(Collectors.toSet());
    }
}