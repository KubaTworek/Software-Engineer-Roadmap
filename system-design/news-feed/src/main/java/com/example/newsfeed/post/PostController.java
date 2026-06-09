package com.example.newsfeed.post;

import com.example.newsfeed.auth.CurrentUser;
import com.example.newsfeed.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Kontroler HTTP odpowiedzialny za operacje na postach.
 *
 * To jest warstwa wejściowa API — przyjmuje requesty od klienta,
 * mapuje je na metody serwisowe i zwraca odpowiedzi HTTP.
 *
 * Nie zawiera logiki biznesowej. Logika tworzenia, pobierania
 * i usuwania posta znajduje się w PostService.
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    /**
     * Serwis domenowy odpowiedzialny za właściwą obsługę postów:
     * zapis do bazy, walidację biznesową, publikację eventów,
     * cache invalidation, moderację itd. — zależnie od etapu aplikacji.
     */
    private final PostService postService;

    /**
     * Wstrzyknięcie PostService przez konstruktor.
     *
     * To preferowany sposób dependency injection,
     * bo zależność jest jawna i niemutowalna.
     */
    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * Tworzy nowy post dla aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * POST /api/v1/posts
     *
     * @CurrentUser wstrzykuje użytkownika wyciągniętego z tokena Authorization.
     * Dzięki temu klient nie przesyła authorId w body — backend sam wie,
     * kto jest autorem posta.
     *
     * @Valid uruchamia walidację CreatePostRequest, np. czy content nie jest pusty
     * i czy nie przekracza dozwolonej długości.
     *
     * Zwraca HTTP 201 CREATED, bo endpoint tworzy nowy zasób.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse createPost(
            @CurrentUser User currentUser,
            @Valid @RequestBody CreatePostRequest request
    ) {
        return postService.createPost(currentUser, request);
    }

    /**
     * Pobiera pojedynczy post po jego ID.
     *
     * Endpoint:
     * GET /api/v1/posts/{id}
     *
     * ID posta jest przekazywane w ścieżce URL jako UUID.
     *
     * PostService powinien zadbać o to, żeby:
     * - zwrócić tylko istniejący post,
     * - nie zwracać posta usuniętego,
     * - ewentualnie sprawdzić widoczność / moderację / uprawnienia,
     *   jeśli aplikacja jest na bardziej zaawansowanym etapie.
     */
    @GetMapping("/{id}")
    public PostResponse getPost(@PathVariable UUID id) {
        return postService.getPost(id);
    }

    /**
     * Usuwa post należący do aktualnie zalogowanego użytkownika.
     *
     * Endpoint:
     * DELETE /api/v1/posts/{id}
     *
     * @CurrentUser jest potrzebny, bo backend musi sprawdzić,
     * czy użytkownik faktycznie jest autorem posta.
     *
     * Kontroler nie wykonuje tej weryfikacji samodzielnie.
     * Robi to PostService, bo jest to logika biznesowa.
     *
     * Zwraca HTTP 204 NO CONTENT, ponieważ operacja nie zwraca body.
     *
     * W praktyce usunięcie powinno być soft delete, czyli ustawienie deletedAt,
     * a nie fizyczne usunięcie rekordu z bazy. Dzięki temu feed, moderacja
     * i eventy mogą nadal bezpiecznie odwoływać się do historii posta.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(
            @CurrentUser User currentUser,
            @PathVariable UUID id
    ) {
        postService.deletePost(currentUser, id);
    }
}