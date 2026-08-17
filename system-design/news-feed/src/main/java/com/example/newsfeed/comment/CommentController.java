package com.example.newsfeed.comment;

import com.example.newsfeed.auth.CurrentUser;
import com.example.newsfeed.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Kontroler HTTP odpowiedzialny za komentarze pod postami.
 *
 * To jest warstwa API:
 * - tworzy komentarz pod postem,
 * - pobiera komentarze posta,
 * - usuwa komentarz użytkownika.
 *
 * Kontroler nie zawiera logiki biznesowej.
 * Nie sprawdza samodzielnie, czy post istnieje, czy komentarz należy do użytkownika,
 * ani czy komentarz powinien trafić do moderacji.
 *
 * Te decyzje należą do CommentService.
 */
@RestController
@RequestMapping("/api/v1")
public class CommentController {

    /**
     * Serwis biznesowy obsługujący komentarze.
     *
     * CommentService odpowiada za:
     * - sprawdzenie, czy post istnieje,
     * - zapis komentarza,
     * - pobieranie komentarzy z paginacją,
     * - soft delete komentarza,
     * - autoryzację właściciela komentarza,
     * - publikację eventów comment.created / comment.deleted,
     * - aktualizację liczników komentarzy.
     */
    private final CommentService commentService;

    /**
     * Wstrzyknięcie CommentService przez konstruktor.
     *
     * Dzięki temu kontroler ma jedną jasną zależność
     * i deleguje całą logikę komentarzy do warstwy serwisowej.
     */
    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * Tworzy komentarz pod konkretnym postem.
     *
     * Endpoint:
     * POST /api/v1/posts/{postId}/comments
     *
     * @CurrentUser dostarcza aktualnie zalogowanego użytkownika.
     * Dzięki temu klient nie wysyła authorId w body i nie może utworzyć
     * komentarza w imieniu innej osoby.
     *
     * @PathVariable postId wskazuje post, pod którym dodajemy komentarz.
     *
     * @Valid uruchamia walidację CreateCommentRequest,
     * np. czy treść komentarza nie jest pusta i nie przekracza limitu znaków.
     *
     * Zwracamy HTTP 201 CREATED, bo powstaje nowy zasób — komentarz.
     */
    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createComment(
            @CurrentUser User currentUser,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return commentService.createComment(currentUser, postId, request);
    }

    /**
     * Pobiera komentarze dla konkretnego posta.
     *
     * Endpoint:
     * GET /api/v1/posts/{postId}/comments
     *
     * Parametry:
     * - limit: maksymalna liczba komentarzy na stronie,
     * - cursor: wskaźnik kolejnej strony.
     *
     * Cursor pagination jest lepsze niż offset pagination,
     * bo komentarze mogą być dodawane w czasie rzeczywistym.
     *
     * CommentService powinien zadbać o to, żeby:
     * - sprawdzić, czy post istnieje,
     * - nie zwracać usuniętych komentarzy,
     * - pobrać komentarze w stabilnej kolejności,
     * - zwrócić nextCursor, jeśli istnieje kolejna strona.
     */
    @GetMapping("/posts/{postId}/comments")
    public CommentPageResponse getComments(
            @PathVariable UUID postId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return commentService.getComments(postId, limit, cursor);
    }

    /**
     * Usuwa komentarz.
     *
     * Endpoint:
     * DELETE /api/v1/comments/{commentId}
     *
     * @CurrentUser jest wymagany, bo system musi sprawdzić,
     * czy aktualny użytkownik jest autorem komentarza.
     *
     * Kontroler nie wykonuje tej weryfikacji.
     * Robi to CommentService.
     *
     * Usunięcie powinno być soft delete:
     * - ustawiamy deletedAt,
     * - nie kasujemy fizycznie rekordu z bazy.
     *
     * Dzięki temu:
     * - zachowujemy historię,
     * - eventy nadal mogą odnosić się do commentId,
     * - licznik komentarzy może zostać zaktualizowany asynchronicznie,
     * - moderacja i audyt mają dostęp do śladu operacji.
     *
     * Zwracamy HTTP 204 NO CONTENT, bo operacja nie zwraca body.
     */
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(
            @CurrentUser User currentUser,
            @PathVariable UUID commentId
    ) {
        commentService.deleteComment(currentUser, commentId);
    }
}