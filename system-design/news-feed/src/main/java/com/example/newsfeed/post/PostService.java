package com.example.newsfeed.post;

import com.example.newsfeed.common.NotFoundException;
import com.example.newsfeed.common.UnauthorizedException;
import com.example.newsfeed.embedding.EmbeddingService;
import com.example.newsfeed.events.*;
import com.example.newsfeed.feature.FeatureStoreService;
import com.example.newsfeed.feed.FeedCacheService;
import com.example.newsfeed.moderation.*;
import com.example.newsfeed.region.RegionGuardService;
import com.example.newsfeed.search.SearchIndexService;
import com.example.newsfeed.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Serwis biznesowy odpowiedzialny za operacje na postach.
 *
 * To tutaj dzieje się realna logika aplikacji:
 * - zapis posta,
 * - moderacja,
 * - generowanie embeddingów,
 * - aktualizacja feature store,
 * - indeksowanie do search,
 * - publikacja eventów do Kafki,
 * - czyszczenie cache feedu,
 * - obsługa soft delete.
 *
 * Controller tylko przyjmuje request HTTP.
 * PostService decyduje, co faktycznie ma się wydarzyć w systemie.
 */
@Service
public class PostService {

    /**
     * Repozytorium odpowiedzialne za zapis i odczyt postów z bazy danych.
     *
     * To jest główne źródło prawdy dla encji Post.
     */
    private final PostRepository postRepository;

    /**
     * Publisher eventów domenowych do Kafki.
     *
     * Po utworzeniu albo usunięciu posta inne części systemu muszą zostać
     * poinformowane asynchronicznie, np. fan-out worker, feed inbox,
     * liczniki, search albo analityka.
     */
    private final KafkaEventPublisher eventPublisher;

    /**
     * Cache feedu w Redis.
     *
     * Po zmianach w postach cache może być nieaktualny, więc trzeba go
     * unieważnić dla feedu globalnego i feedu autora.
     */
    private final FeedCacheService feedCacheService;

    /**
     * Automatyczna i manualna moderacja treści.
     *
     * Przed dystrybucją posta do feedów system sprawdza, czy treść
     * nie powinna zostać ukryta albo skierowana do review.
     */
    private final ModerationService moderationService;

    /**
     * Serwis embeddingów.
     *
     * Tworzy wektorową reprezentację posta, która później może być używana
     * do rekomendacji, podobieństwa treści i personalizacji feedu.
     */
    private final EmbeddingService embeddingService;

    /**
     * Feature Store dla modeli rankingowych i rekomendacyjnych.
     *
     * Po utworzeniu posta zakładamy bazowe feature’y, np. quality_score,
     * spam_score, CTR, report_rate.
     */
    private final FeatureStoreService featureStoreService;

    /**
     * Indeks wyszukiwarki.
     *
     * Po utworzeniu posta trafia on do search indexu,
     * a po usunięciu powinien z niego zniknąć.
     */
    private final SearchIndexService searchIndexService;

    /**
     * Ochrona przed zapisem w niewłaściwym regionie.
     *
     * W architekturze multi-region tylko wybrany region powinien obsługiwać zapisy.
     * Read replica nie może tworzyć ani usuwać postów.
     */
    private final RegionGuardService regionGuardService;

    /**
     * Konstruktor wstrzykujący wszystkie zależności potrzebne do obsługi posta.
     *
     * Liczba zależności pokazuje, że stworzenie posta w dojrzałej aplikacji
     * to nie tylko INSERT do bazy, ale uruchomienie całego pipeline’u:
     * moderation, ML, search, events, cache.
     */
    public PostService(PostRepository postRepository, KafkaEventPublisher eventPublisher, FeedCacheService feedCacheService,
                       ModerationService moderationService, EmbeddingService embeddingService,
                       FeatureStoreService featureStoreService, SearchIndexService searchIndexService,
                       RegionGuardService regionGuardService) {
        this.postRepository = postRepository;
        this.eventPublisher = eventPublisher;
        this.feedCacheService = feedCacheService;
        this.moderationService = moderationService;
        this.embeddingService = embeddingService;
        this.featureStoreService = featureStoreService;
        this.searchIndexService = searchIndexService;
        this.regionGuardService = regionGuardService;
    }

    /**
     * Tworzy nowy post i uruchamia pipeline dystrybucji treści.
     *
     * Cała metoda działa w transakcji, więc podstawowy zapis posta oraz
     * operacje wykonywane synchronicznie w tej metodzie są częścią jednej
     * jednostki pracy.
     *
     * Najważniejszy flow:
     * 1. sprawdzenie, czy ten region może wykonywać zapisy,
     * 2. zapis posta w bazie,
     * 3. automatyczna moderacja,
     * 4. wygenerowanie embeddingu,
     * 5. utworzenie domyślnych feature’ów,
     * 6. indeksacja do search,
     * 7. publikacja eventu post.created, jeśli post nie został auto-hidden,
     * 8. czyszczenie cache feedu.
     */
    @Transactional
    public PostResponse createPost(User author, CreatePostRequest request) {
        /*
         * Multi-region safety.
         *
         * Jeżeli aplikacja działa w regionie tylko do odczytu,
         * ta metoda powinna zakończyć się błędem przed jakimkolwiek zapisem.
         */
        regionGuardService.requireWriteRegion();

        /*
         * Utworzenie encji posta.
         *
         * Autor nie pochodzi z request body, tylko z kontekstu zalogowanego użytkownika.
         * To zabezpiecza przed podszyciem się pod innego autora.
         */
        Instant now = Instant.now();
        Post post = new Post(UUID.randomUUID(), author, request.content().trim(), request.topics(), now, now, null);

        /*
         * Zapis posta do głównej bazy.
         *
         * Od tego momentu post istnieje jako rekord systemowy,
         * ale niekoniecznie został już rozdystrybuowany do feedów.
         */
        Post savedPost = postRepository.save(post);

        /*
         * Moderacja treści.
         *
         * Jeżeli post zostanie oznaczony jako auto_hidden,
         * nie publikujemy eventu post.created, więc fan-out worker
         * nie wrzuci go do feedów użytkowników.
         */
        ModerationDecision decision = moderationService.createReviewIfNeeded(
                "post",
                savedPost.getId(),
                savedPost.getContent()
        );

        /*
         * Embedding posta.
         *
         * Łączymy content i topics, żeby rekomendacje mogły brać pod uwagę
         * zarówno tekst posta, jak i jego kategorie tematyczne.
         */
        embeddingService.savePostEmbedding(
                savedPost.getId(),
                savedPost.getContent() + " " + String.join(" ", savedPost.getTopics())
        );

        /*
         * Feature Store.
         *
         * Tworzymy bazowe feature’y posta, które później będą aktualizowane
         * przez pipeline analityczny, np. CTR, spam_score, quality_score.
         */
        featureStoreService.upsertDefaultPostFeatures(savedPost.getId());

        /*
         * Search indexing.
         *
         * Post trafia do indeksu wyszukiwarki, żeby użytkownicy mogli go znaleźć
         * przez endpoint search.
         */
        searchIndexService.indexPost(savedPost);

        /*
         * Publikacja eventu post.created.
         *
         * Event jest potrzebny do asynchronicznych procesów:
         * - fan-out on write,
         * - aktualizacja feed_inbox,
         * - rekomendacje,
         * - analityka,
         * - inne projekcje danych.
         *
         * Jeśli moderacja ukryła post automatycznie, nie dystrybuujemy go dalej.
         */
        if (!"auto_hidden".equals(decision.status())) {
            DomainEvent event = DomainEvent.of(
                    NewsFeedTopics.POST_CREATED,
                    author.getId(),
                    savedPost.getId(),
                    Map.of(
                            "authorId", author.getId().toString(),
                            "postId", savedPost.getId().toString(),
                            "createdAt", savedPost.getCreatedAt().toString()
                    )
            );

            /*
             * Kluczem eventu jest authorId.
             *
             * Dzięki temu eventy jednego autora mogą trafiać do tej samej partycji Kafki,
             * co pomaga zachować ich kolejność w obrębie autora.
             */
            eventPublisher.publish(NewsFeedTopics.POST_CREATED, author.getId().toString(), event);
        }

        /*
         * Cache invalidation.
         *
         * Po stworzeniu posta feed globalny i feed autora mogą być nieaktualne.
         * Czyścimy cache, żeby kolejne odczyty mogły pobrać świeższe dane.
         */
        feedCacheService.evictGlobalFeed();
        feedCacheService.evictPersonalizedFeed(author.getId());

        /*
         * Zwracamy DTO, a nie encję JPA.
         *
         * Dzięki temu API nie ujawnia wewnętrznego modelu bazy danych.
         */
        return PostResponse.from(savedPost);
    }

    /**
     * Pobiera pojedynczy post po ID.
     *
     * readOnly = true oznacza, że metoda nie powinna modyfikować danych.
     * To jest czytelny sygnał dla Springa i dla osoby czytającej kod.
     *
     * Metoda nie zwraca postów usuniętych, bo korzysta z:
     * findByIdAndDeletedAtIsNull.
     */
    @Transactional(readOnly = true)
    public PostResponse getPost(UUID id) {
        /*
         * Szukamy tylko aktywnego posta.
         *
         * Jeśli post nie istnieje albo został usunięty przez soft delete,
         * zwracamy kontrolowany wyjątek 404.
         */
        Post post = postRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Post not found."));

        return PostResponse.from(post);
    }

    /**
     * Usuwa post aktualnie zalogowanego użytkownika.
     *
     * W praktyce jest to soft delete:
     * post zostaje w bazie, ale dostaje deletedAt.
     *
     * Dzięki temu:
     * - nie gubimy historii,
     * - eventy nadal mogą odnosić się do postId,
     * - feed worker może usunąć referencje z feed_inbox,
     * - search może usunąć dokument z indeksu.
     */
    @Transactional
    public void deletePost(User currentUser, UUID id) {
        /*
         * Tak jak przy tworzeniu posta, operacje zapisu są dozwolone
         * tylko w regionie właścicielskim.
         */
        regionGuardService.requireWriteRegion();

        /*
         * Pobieramy tylko aktywny post.
         *
         * Jeżeli post już był usunięty, traktujemy go jak nieistniejący.
         */
        Post post = postRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Post not found."));

        /*
         * Autoryzacja właściciela.
         *
         * Użytkownik może usunąć tylko własny post.
         * To jest logika biznesowa, dlatego znajduje się w serwisie,
         * a nie w kontrolerze.
         */
        if (!post.getAuthor().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("You can delete only your own posts.");
        }

        /*
         * Soft delete.
         *
         * Nie usuwamy fizycznie rekordu z bazy.
         * Ustawienie deletedAt wystarcza, żeby post zniknął z API i feedu.
         */
        post.softDelete();

        /*
         * Usuwamy dokument z indeksu wyszukiwarki.
         *
         * Dzięki temu post nie będzie pojawiał się w wynikach search.
         */
        searchIndexService.deletePost(post.getId().toString());

        /*
         * Event post.deleted.
         *
         * Inne komponenty systemu dostają informację, że post został usunięty.
         * Fan-out/feed worker może na tej podstawie wyczyścić feed_inbox.
         */
        DomainEvent event = DomainEvent.of(
                NewsFeedTopics.POST_DELETED,
                currentUser.getId(),
                post.getId(),
                Map.of("postId", post.getId().toString())
        );

        /*
         * Kluczem eventu jest postId.
         *
         * To pomaga utrzymać kolejność eventów dotyczących konkretnego posta.
         */
        eventPublisher.publish(NewsFeedTopics.POST_DELETED, post.getId().toString(), event);

        /*
         * Cache invalidation.
         *
         * Po usunięciu posta feed globalny i feed autora mogą zawierać
         * nieaktualną referencję, więc czyścimy cache.
         */
        feedCacheService.evictGlobalFeed();
        feedCacheService.evictPersonalizedFeed(currentUser.getId());
    }
}