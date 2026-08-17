package com.example.filestorage.quota;

import com.example.filestorage.auth.AppUser;
import com.example.filestorage.auth.UserRepository;
import com.example.filestorage.config.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/**
 * REST controller odpowiedzialny za informacje o limicie przestrzeni użytkownika.
 *
 * Ten endpoint pozwala klientowi pokazać użytkownikowi:
 * - ile miejsca już wykorzystał,
 * - jaki ma całkowity limit,
 * - ile miejsca mu zostało,
 * - jaki procent quota jest zajęty.
 *
 * Quota jest liczona na podstawie danych zapisanych na użytkowniku,
 * a nie przez skanowanie wszystkich plików przy każdym requestcie.
 */
@RestController
@RequestMapping("/api/v1/quota")
public class QuotaController {

    /**
     * Repozytorium użytkowników.
     *
     * W tej klasie służy do pobrania aktualnych wartości:
     * - storageUsedBytes,
     * - storageQuotaBytes.
     *
     * W większej aplikacji można rozważyć wydzielenie QuotaService,
     * żeby controller nie korzystał bezpośrednio z repozytorium.
     */
    private final UserRepository userRepository;

    public QuotaController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Zwraca aktualny stan quota zalogowanego użytkownika.
     *
     * Endpoint:
     * GET /api/v1/quota
     *
     * currentUser:
     * użytkownik pobrany z kontekstu autoryzacji.
     *
     * Zwracane wartości:
     * - usedBytes: ile bajtów użytkownik już zajmuje,
     * - quotaBytes: całkowity limit użytkownika,
     * - remainingBytes: ile bajtów jeszcze zostało,
     * - usedPercent: procent wykorzystania limitu.
     */
    @GetMapping
    public QuotaResponse get(CurrentUser currentUser) {
        /*
         * Pobieramy użytkownika z bazy, bo quota jest przechowywana
         * bezpośrednio na encji AppUser.
         *
         * Jeśli użytkownik z tokena nie istnieje w bazie, traktujemy to jako błąd spójności.
         */
        AppUser user = userRepository.findById(currentUser.id())
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        /*
         * remaining nie może spaść poniżej zera.
         *
         * Teoretycznie storageUsedBytes nie powinno przekroczyć storageQuotaBytes,
         * ale przy błędach, migracjach albo zmianach limitów może się to zdarzyć.
         */
        long remaining = Math.max(
                0,
                user.getStorageQuotaBytes() - user.getStorageUsedBytes()
        );

        /*
         * Procent wykorzystania quota.
         *
         * Jeśli quota wynosi 0, traktujemy konto jako w pełni wykorzystane.
         * To zabezpiecza przed dzieleniem przez zero.
         */
        double usedPercent = user.getStorageQuotaBytes() == 0
                ? 100.0
                : (user.getStorageUsedBytes() * 100.0) / user.getStorageQuotaBytes();

        /*
         * Controller zwraca DTO, a nie encję AppUser.
         * Dzięki temu API pokazuje tylko dane potrzebne klientowi.
         */
        return new QuotaResponse(
                user.getStorageUsedBytes(),
                user.getStorageQuotaBytes(),
                remaining,
                usedPercent
        );
    }
}