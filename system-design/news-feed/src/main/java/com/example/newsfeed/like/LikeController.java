package com.example.newsfeed.like;

import com.example.newsfeed.auth.CurrentUser;
import com.example.newsfeed.user.User;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Kontroler HTTP odpowiedzialny za lajkowanie i odlajkowywanie postów.
 *
 * To jest cienka warstwa API:
 * - odbiera request,
 * - pobiera aktualnie zalogowanego użytkownika,
 * - pobiera postId z URL,
 * - przekazuje operację do LikeService.
 *
 * Kontroler nie powinien zawierać logiki biznesowej typu:
 * - czy post istnieje,
 * - czy użytkownik już polubił post,
 * - jak aktualizować licznik lajków,
 * - czy publikować event do Kafki.
 *
 * To wszystko należy do LikeService.
 */
@RestController
@RequestMapping("/api/v1/posts/{postId}")
public class LikeController {

    /**
     * Serwis biznesowy obsługujący lajki.
     *
     * LikeService odpowiada za:
     * - zapis lajka,
     * - usunięcie lajka,
     * - idempotencję operacji,
     * - aktualizację liczników,
     * - publikację eventów typu post.liked / post.unliked.
     */
    private final LikeService likeService;

    /**
     * Wstrzyknięcie LikeService przez konstruktor.
     *
     * Dzięki temu zależność jest jawna, niemutowalna
     * i łatwa do podstawienia w testach.
     */
    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    /**
     * Dodaje lajka do posta.
     *
     * Endpoint:
     * POST /api/v1/posts/{postId}/like
     *
     * @CurrentUser dostarcza użytkownika na podstawie tokena Authorization.
     * Klient nie przesyła userId w body, dzięki czemu nie może polubić posta
     * w imieniu innego użytkownika.
     *
     * @PathVariable UUID postId pobiera ID posta z adresu URL.
     *
     * LikeService powinien zadbać o to, żeby:
     * - sprawdzić, czy post istnieje i nie jest usunięty,
     * - nie dodać duplikatu lajka,
     * - zwrócić aktualny stan liked=true,
     * - zwrócić aktualną liczbę lajków,
     * - opublikować event do asynchronicznej aktualizacji liczników.
     */
    @PostMapping("/like")
    public LikeResponse like(
            @CurrentUser User currentUser,
            @PathVariable UUID postId
    ) {
        return likeService.like(currentUser, postId);
    }

    /**
     * Usuwa lajka z posta.
     *
     * Endpoint:
     * DELETE /api/v1/posts/{postId}/like
     *
     * Operacja powinna być bezpieczna i idempotentna:
     * jeśli użytkownik wcześniej nie polubił posta, system nadal może zwrócić
     * poprawny stan liked=false zamiast traktować to jako błąd.
     *
     * LikeService powinien zadbać o:
     * - usunięcie relacji user-post z tabeli lajków,
     * - aktualizację albo asynchroniczne przeliczenie licznika,
     * - publikację eventu post.unliked,
     * - zwrócenie aktualnego stanu posta z perspektywy użytkownika.
     */
    @DeleteMapping("/like")
    public LikeResponse unlike(
            @CurrentUser User currentUser,
            @PathVariable UUID postId
    ) {
        return likeService.unlike(currentUser, postId);
    }
}