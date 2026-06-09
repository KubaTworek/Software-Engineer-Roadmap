package com.example.newsfeed.like;

import com.example.newsfeed.common.NotFoundException;
import com.example.newsfeed.events.DomainEvent;
import com.example.newsfeed.events.KafkaEventPublisher;
import com.example.newsfeed.events.NewsFeedTopics;
import com.example.newsfeed.post.Post;
import com.example.newsfeed.post.PostRepository;
import com.example.newsfeed.user.User;
import com.example.newsfeed.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Serwis biznesowy odpowiedzialny za obsługę lajków postów.
 *
 * To tutaj znajduje się właściwa logika:
 * - sprawdzenie, czy post istnieje,
 * - sprawdzenie, czy użytkownik istnieje,
 * - dodanie lajka,
 * - usunięcie lajka,
 * - zabezpieczenie przed duplikatami,
 * - publikacja eventów do Kafki,
 * - zwrócenie aktualnego stanu lajka i licznika.
 *
 * Controller tylko wystawia endpoint HTTP.
 * LikeService decyduje, jak operacja wpływa na system.
 */
@Service
public class LikeService {

    /**
     * Repozytorium relacji user-post.
     *
     * Przechowuje informację, który użytkownik polubił który post.
     * Najczęściej tabela ma klucz złożony:
     * post_id + user_id.
     *
     * Dzięki temu jeden użytkownik może polubić dany post tylko raz.
     */
    private final PostLikeRepository postLikeRepository;

    /**
     * Repozytorium postów.
     *
     * Używane do sprawdzenia, czy post istnieje i nie został usunięty.
     * Nie pozwalamy lajkować postów z deletedAt != null.
     */
    private final PostRepository postRepository;

    /**
     * Repozytorium użytkowników.
     *
     * Używane do pobrania aktualnego użytkownika jako encji zarządzanej przez JPA.
     * currentUser z kontrolera może pochodzić z warstwy auth, więc tutaj
     * dociągamy użytkownika z bazy.
     */
    private final UserRepository userRepository;

    /**
     * Publisher eventów domenowych.
     *
     * Lajk i odlajkowanie są istotne dla innych części systemu:
     * - async counter worker aktualizuje licznik lajków,
     * - ranking może używać lajków jako sygnału popularności,
     * - rekomendacje mogą traktować lajki jako sygnał preferencji,
     * - analityka może liczyć engagement.
     */
    private final KafkaEventPublisher eventPublisher;

    /**
     * Wstrzyknięcie zależności przez konstruktor.
     *
     * Serwis potrzebuje repozytoriów do operacji synchronicznych
     * i publishera do uruchamiania procesów asynchronicznych.
     */
    public LikeService(
            PostLikeRepository postLikeRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            KafkaEventPublisher eventPublisher
    ) {
        this.postLikeRepository = postLikeRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Dodaje lajka do posta.
     *
     * Metoda jest transakcyjna, bo zapis lajka i decyzja o publikacji eventu
     * powinny być traktowane jako jedna operacja biznesowa.
     *
     * Flow:
     * 1. sprawdź, czy post istnieje i nie jest usunięty,
     * 2. pobierz aktualnego użytkownika z bazy,
     * 3. zbuduj klucz złożony postId + userId,
     * 4. jeśli lajk jeszcze nie istnieje — zapisz go,
     * 5. opublikuj event post.liked,
     * 6. zwróć aktualny stan liked=true i licznik lajków.
     */
    @Transactional
    public LikeResponse like(User currentUser, UUID postId) {
        /*
         * Pobieramy tylko aktywny post.
         *
         * Jeżeli post nie istnieje albo został usunięty przez soft delete,
         * użytkownik nie może go polubić.
         */
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new NotFoundException("Post not found."));

        /*
         * Pobieramy użytkownika z bazy.
         *
         * currentUser identyfikuje zalogowanego użytkownika,
         * ale tutaj chcemy mieć pełną encję User zarządzaną przez JPA.
         */
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new NotFoundException("User not found."));

        /*
         * Klucz złożony lajka.
         *
         * Jeden rekord oznacza:
         * konkretny użytkownik polubił konkretny post.
         */
        PostLikeId id = new PostLikeId(post.getId(), user.getId());

        /*
         * Idempotencja operacji like.
         *
         * Jeśli użytkownik już polubił ten post, nie tworzymy drugiego rekordu
         * i nie publikujemy drugiego eventu.
         *
         * Dzięki temu wielokrotne kliknięcie "like" nie zawyża liczników.
         */
        if (!postLikeRepository.existsById(id)) {
            /*
             * Zapis relacji user-post.
             *
             * To jest źródło prawdy mówiące, że użytkownik polubił post.
             */
            postLikeRepository.save(new PostLike(id, post, user, Instant.now()));

            /*
             * Event post.liked.
             *
             * Publikujemy go tylko wtedy, gdy faktycznie powstał nowy lajk.
             * Dzięki temu async counter worker może bezpiecznie zwiększyć licznik.
             */
            eventPublisher.publish(
                    NewsFeedTopics.POST_LIKED,
                    postId.toString(),
                    DomainEvent.of(
                            NewsFeedTopics.POST_LIKED,
                            user.getId(),
                            postId,
                            Map.of("postId", postId.toString())
                    )
            );
        }

        /*
         * Zwracamy aktualny stan z perspektywy użytkownika.
         *
         * liked=true, bo po tej operacji post jest polubiony,
         * nawet jeśli lajk istniał już wcześniej.
         *
         * countByIdPostId daje aktualny licznik z tabeli lajków.
         * W większej skali można tu czytać z async projection / counter_shards.
         */
        return new LikeResponse(
                postId,
                true,
                postLikeRepository.countByIdPostId(postId)
        );
    }

    /**
     * Usuwa lajka z posta.
     *
     * Operacja jest idempotentna:
     * jeśli lajk nie istnieje, metoda nie rzuca błędu,
     * tylko zwraca stan liked=false.
     *
     * Flow:
     * 1. zbuduj klucz postId + currentUserId,
     * 2. jeśli lajk istnieje — usuń go,
     * 3. opublikuj event post.unliked,
     * 4. zwróć aktualny stan liked=false i licznik lajków.
     */
    @Transactional
    public LikeResponse unlike(User currentUser, UUID postId) {
        /*
         * Klucz relacji lajka.
         *
         * Nie musimy pobierać całego posta, żeby usunąć relację,
         * bo do delete wystarczy klucz postId + userId.
         */
        PostLikeId id = new PostLikeId(postId, currentUser.getId());

        /*
         * Idempotencja operacji unlike.
         *
         * Event publikujemy tylko wtedy, gdy faktycznie usunęliśmy istniejący lajk.
         * Dzięki temu licznik nie zostanie zmniejszony kilka razy.
         */
        if (postLikeRepository.existsById(id)) {
            /*
             * Usunięcie relacji user-post.
             *
             * Po tej operacji użytkownik nie lubi już tego posta.
             */
            postLikeRepository.deleteById(id);

            /*
             * Event post.unliked.
             *
             * Konsumenci asynchroniczni mogą na jego podstawie:
             * - zmniejszyć licznik lajków,
             * - zaktualizować ranking,
             * - zapisać sygnał engagementu użytkownika.
             */
            eventPublisher.publish(
                    NewsFeedTopics.POST_UNLIKED,
                    postId.toString(),
                    DomainEvent.of(
                            NewsFeedTopics.POST_UNLIKED,
                            currentUser.getId(),
                            postId,
                            Map.of("postId", postId.toString())
                    )
            );
        }

        /*
         * Zwracamy aktualny stan z perspektywy użytkownika.
         *
         * liked=false, bo po tej operacji post nie jest polubiony,
         * nawet jeśli wcześniej również nie był.
         */
        return new LikeResponse(
                postId,
                false,
                postLikeRepository.countByIdPostId(postId)
        );
    }
}