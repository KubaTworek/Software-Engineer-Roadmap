package com.example.newsfeed.comment;

import com.example.newsfeed.common.NotFoundException;
import com.example.newsfeed.common.UnauthorizedException;
import com.example.newsfeed.events.DomainEvent;
import com.example.newsfeed.events.KafkaEventPublisher;
import com.example.newsfeed.events.NewsFeedTopics;
import com.example.newsfeed.post.Post;
import com.example.newsfeed.post.PostRepository;
import com.example.newsfeed.user.User;
import com.example.newsfeed.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Serwis biznesowy odpowiedzialny za komentarze.
 *
 * To tutaj znajduje się właściwa logika aplikacji:
 * - sprawdzenie, czy post istnieje,
 * - zapis komentarza,
 * - pobieranie komentarzy,
 * - autoryzacja usuwania komentarza,
 * - soft delete,
 * - publikacja eventów do Kafki.
 *
 * Controller tylko wystawia endpointy HTTP.
 * CommentService decyduje, jak komentarze wpływają na system.
 */
@Service
public class CommentService {

    /**
     * Domyślna liczba komentarzy zwracanych na jednej stronie.
     *
     * Używana, gdy klient nie poda parametru limit.
     */
    private static final int DEFAULT_LIMIT = 20;

    /**
     * Maksymalna liczba komentarzy na jednej stronie.
     *
     * Chroni system przed requestami typu limit=100000,
     * które mogłyby przeciążyć bazę danych.
     */
    private static final int MAX_LIMIT = 50;

    /**
     * Repozytorium komentarzy.
     *
     * Odpowiada za zapis, odczyt i soft delete komentarzy.
     */
    private final CommentRepository commentRepository;

    /**
     * Repozytorium postów.
     *
     * Potrzebne, żeby sprawdzić, czy komentowany post istnieje
     * i nie został usunięty.
     */
    private final PostRepository postRepository;

    /**
     * Repozytorium użytkowników.
     *
     * Służy do pobrania autora komentarza jako encji zarządzanej przez JPA.
     */
    private final UserRepository userRepository;

    /**
     * Publisher eventów domenowych.
     *
     * Komentarze wpływają na inne części systemu:
     * - licznik komentarzy,
     * - ranking posta,
     * - engagement,
     * - rekomendacje,
     * - analitykę.
     *
     * Dlatego po utworzeniu lub usunięciu komentarza publikujemy event.
     */
    private final KafkaEventPublisher eventPublisher;

    /**
     * Wstrzyknięcie zależności przez konstruktor.
     *
     * Serwis potrzebuje repozytoriów do operacji synchronicznych
     * oraz publishera do uruchamiania procesów asynchronicznych.
     */
    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            KafkaEventPublisher eventPublisher
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Tworzy komentarz pod konkretnym postem.
     *
     * Metoda jest transakcyjna, ponieważ zapis komentarza i decyzja
     * o publikacji eventu są jedną operacją biznesową.
     *
     * Flow:
     * 1. sprawdź, czy post istnieje i nie jest usunięty,
     * 2. pobierz aktualnego użytkownika z bazy,
     * 3. utwórz komentarz,
     * 4. zapisz komentarz,
     * 5. opublikuj event comment.created,
     * 6. zwróć DTO komentarza.
     */
    @Transactional
    public CommentResponse createComment(User currentUser, UUID postId, CreateCommentRequest request) {
        /*
         * Pobieramy tylko aktywny post.
         *
         * Jeżeli post nie istnieje albo został usunięty,
         * nie pozwalamy dodać komentarza.
         */
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("Post not found."));

        /*
         * Pobieramy autora komentarza z bazy.
         *
         * currentUser pochodzi z warstwy autoryzacji,
         * ale tutaj chcemy mieć encję User zarządzaną przez JPA.
         */
        User author = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new NotFoundException("User not found."));

        /*
         * Tworzymy encję komentarza.
         *
         * Treść jest trimowana, żeby nie zapisywać przypadkowych spacji
         * na początku lub końcu komentarza.
         */
        Instant now = Instant.now();
        Comment comment = new Comment(
                UUID.randomUUID(),
                post,
                author,
                request.content().trim(),
                now,
                now,
                null
        );

        /*
         * Zapis komentarza w bazie.
         *
         * Od tego momentu komentarz istnieje jako źródło prawdy.
         */
        Comment saved = commentRepository.save(comment);

        /*
         * Event comment.created.
         *
         * Publikujemy go po zapisie komentarza, żeby inne komponenty mogły
         * zareagować asynchronicznie.
         *
         * Typowe konsumery:
         * - async counter worker zwiększa comment_count,
         * - ranking podbija engagement posta,
         * - analityka zapisuje aktywność użytkownika,
         * - recommendation pipeline aktualizuje preferencje.
         */
        eventPublisher.publish(
                NewsFeedTopics.COMMENT_CREATED,
                postId.toString(),
                DomainEvent.of(
                        NewsFeedTopics.COMMENT_CREATED,
                        currentUser.getId(),
                        saved.getId(),
                        Map.of(
                                "postId", postId.toString(),
                                "commentId", saved.getId().toString()
                        )
                )
        );

        /*
         * Zwracamy DTO, a nie encję JPA.
         *
         * Dzięki temu API nie ujawnia wewnętrznej struktury bazy danych.
         */
        return CommentResponse.from(saved);
    }

    /**
     * Pobiera komentarze konkretnego posta.
     *
     * readOnly = true oznacza, że metoda nie modyfikuje danych.
     *
     * Aktualna implementacja pobiera pierwszą stronę komentarzy.
     * Parametr cursor jest przyjęty w sygnaturze, ale tutaj nie jest jeszcze użyty.
     *
     * W bardziej kompletnej wersji cursor powinien wskazywać ostatni komentarz
     * z poprzedniej strony, np. przez createdAt + commentId.
     */
    @Transactional(readOnly = true)
    public CommentPageResponse getComments(UUID postId, Integer requestedLimit, String encodedCursor) {
        /*
         * Sprawdzamy, czy post istnieje i nie został usunięty.
         *
         * Nie używamy existsById, bo ono zwróci true również dla posta,
         * który istnieje w bazie, ale ma ustawione deletedAt.
         */
        postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("Post not found."));

        /*
         * Normalizacja limitu.
         *
         * Pobieramy maksymalnie MAX_LIMIT komentarzy,
         * żeby jeden request nie przeciążył bazy.
         */
        int limit = requestedLimit == null || requestedLimit < 1
                ? DEFAULT_LIMIT
                : Math.min(requestedLimit, MAX_LIMIT);

        /*
         * Dekodujemy cursor.
         *
         * Brak cursora oznacza pierwszą stronę.
         * Obecny cursor oznacza kolejną stronę po konkretnym komentarzu.
         */
        Optional<CommentCursor> cursor = CommentCursor.decode(encodedCursor);

        /*
         * Pobieramy limit + 1 rekordów.
         *
         * Ten dodatkowy rekord służy tylko do sprawdzenia,
         * czy istnieje kolejna strona.
         *
         * Do response zwrócimy maksymalnie limit rekordów.
         */
        List<Comment> comments = cursor
                .map(commentCursor -> commentRepository.findNextPage(
                        postId,
                        commentCursor.createdAt(),
                        commentCursor.id(),
                        PageRequest.of(0, limit + 1)
                ))
                .orElseGet(() -> commentRepository.findFirstPage(
                        postId,
                        PageRequest.of(0, limit + 1)
                ));

        /*
         * Jeśli pobraliśmy więcej niż limit, oznacza to,
         * że istnieje następna strona komentarzy.
         */
        boolean hasNext = comments.size() > limit;

        /*
         * Do klienta zwracamy tylko limit elementów.
         * Rekord limit + 1 był potrzebny wyłącznie technicznie.
         */
        List<Comment> pageItems = hasNext
                ? comments.subList(0, limit)
                : comments;

        /*
         * Budujemy nextCursor na podstawie ostatniego komentarza
         * faktycznie zwróconego klientowi.
         *
         * Cursor będzie użyty przez klienta w kolejnym requestcie.
         */
        String nextCursor = null;
        if (hasNext && !pageItems.isEmpty()) {
            Comment lastComment = pageItems.get(pageItems.size() - 1);
            nextCursor = new CommentCursor(
                    lastComment.getCreatedAt(),
                    lastComment.getId()
            ).encode();
        }

        /*
         * Zwracamy komentarze oraz cursor do następnej strony.
         *
         * Jeśli nextCursor == null, klient wie, że nie ma kolejnej strony.
         */
        return new CommentPageResponse(
                pageItems.stream()
                        .map(CommentResponse::from)
                        .toList(),
                nextCursor
        );
    }

    /**
     * Usuwa komentarz aktualnie zalogowanego użytkownika.
     *
     * Jest to soft delete:
     * - komentarz zostaje w bazie,
     * - ustawiane jest deletedAt,
     * - komentarz znika z publicznych odczytów.
     *
     * Flow:
     * 1. znajdź aktywny komentarz,
     * 2. sprawdź, czy aktualny użytkownik jest jego autorem,
     * 3. wykonaj soft delete,
     * 4. opublikuj event comment.deleted.
     */
    @Transactional
    public void deleteComment(User currentUser, UUID commentId) {
        /*
         * Pobieramy tylko aktywny komentarz.
         *
         * Jeśli komentarz nie istnieje albo został już usunięty,
         * zwracamy 404.
         */
        Comment comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new NotFoundException("Comment not found."));

        /*
         * Autoryzacja właściciela.
         *
         * Użytkownik może usunąć tylko własny komentarz.
         *
         * To jest logika biznesowa, więc znajduje się w serwisie,
         * a nie w kontrolerze.
         */
        if (!comment.getAuthor().getId().equals(currentUser.getId())) {
            throw new UnauthorizedException("You can delete only your own comments.");
        }

        /*
         * Soft delete komentarza.
         *
         * Nie usuwamy rekordu fizycznie, bo:
         * - zachowujemy historię,
         * - moderacja i audyt mogą analizować treść,
         * - eventy mogą nadal odnosić się do commentId.
         */
        comment.softDelete();

        /*
         * Event comment.deleted.
         *
         * Inne komponenty systemu mogą zareagować asynchronicznie:
         * - zmniejszyć licznik komentarzy,
         * - przeliczyć ranking posta,
         * - usunąć komentarz z widoków/projekcji,
         * - zapisać informację analityczną.
         */
        eventPublisher.publish(
                NewsFeedTopics.COMMENT_DELETED,
                comment.getPost().getId().toString(),
                DomainEvent.of(
                        NewsFeedTopics.COMMENT_DELETED,
                        currentUser.getId(),
                        commentId,
                        Map.of(
                                "postId", comment.getPost().getId().toString(),
                                "commentId", commentId.toString()
                        )
                )
        );
    }
}