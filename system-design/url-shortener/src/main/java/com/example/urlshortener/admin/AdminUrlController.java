package com.example.urlshortener.admin;

import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.service.ShortUrlService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

/**
 * Kontroler REST odpowiedzialny za operacje administracyjne na skróconych URL-ach.
 *
 * <p>
 * Ten kontroler udostępnia endpointy pozwalające administratorowi blokować
 * oraz odblokowywać konkretne short code'y.
 * </p>
 *
 * <p>
 * Wszystkie endpointy w tej klasie są dostępne pod wspólnym prefiksem:
 * </p>
 *
 * <pre>
 * /api/v1/admin/urls
 * </pre>
 *
 * <p>
 * Przykładowe endpointy:
 * </p>
 *
 * <pre>
 * POST /api/v1/admin/urls/{shortCode}/block
 * POST /api/v1/admin/urls/{shortCode}/unblock
 * </pre>
 *
 * <p>
 * Dostęp do endpointów jest chroniony prostym mechanizmem administracyjnym
 * opartym o nagłówek:
 * </p>
 *
 * <pre>
 * X-Admin-Token
 * </pre>
 *
 * <p>
 * Token jest weryfikowany przez {@link AdminAuthService}. Jeśli token jest
 * niepoprawny albo go brakuje, serwis powinien rzucić wyjątek, który następnie
 * zostanie obsłużony przez globalny mechanizm obsługi błędów.
 * </p>
 *
 * <p>
 * Ta klasa nie zawiera logiki biznesowej blokowania URL-i. Deleguje ją do
 * {@link ShortUrlService}. Dzięki temu kontroler pozostaje cienką warstwą HTTP,
 * a logika domenowa jest utrzymana w serwisie aplikacyjnym.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/admin/urls")
public class AdminUrlController {

    /**
     * Serwis odpowiedzialny za sprawdzanie uprawnień administracyjnych.
     *
     * <p>
     * W tej implementacji kontroler pobiera token z nagłówka HTTP
     * {@code X-Admin-Token}, a następnie przekazuje go do metody
     * {@code requireAdmin()}.
     * </p>
     *
     * <p>
     * Jeśli token jest poprawny, metoda kończy się bez wyjątku.
     * Jeśli token jest niepoprawny, brakujący albo administrator nie ma dostępu,
     * metoda powinna rzucić wyjątek, np. {@code UnauthorizedException}
     * albo {@code ForbiddenException}, zależnie od implementacji.
     * </p>
     */
    private final AdminAuthService adminAuthService;

    /**
     * Serwis domenowy obsługujący operacje na skróconych URL-ach.
     *
     * <p>
     * Kontroler używa go do:
     * </p>
     *
     * <ul>
     *     <li>blokowania short code'a,</li>
     *     <li>odblokowywania short code'a,</li>
     *     <li>zwrócenia aktualnych szczegółów URL-a po operacji.</li>
     * </ul>
     */
    private final ShortUrlService shortUrlService;

    /**
     * Konstruktor kontrolera.
     *
     * <p>
     * Spring wstrzykuje tutaj wymagane zależności przez constructor injection.
     * Jest to preferowane podejście, ponieważ zależności klasy są jawne,
     * łatwe do przetestowania i nie wymagają refleksyjnego wstrzykiwania pól.
     * </p>
     *
     * @param adminAuthService serwis autoryzacji administratora
     * @param shortUrlService serwis obsługi skróconych URL-i
     */
    public AdminUrlController(AdminAuthService adminAuthService, ShortUrlService shortUrlService) {
        this.adminAuthService = adminAuthService;
        this.shortUrlService = shortUrlService;
    }

    /**
     * Blokuje wskazany short code.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * POST /api/v1/admin/urls/{shortCode}/block
     * </pre>
     *
     * <p>
     * Wymagany nagłówek:
     * </p>
     *
     * <pre>
     * X-Admin-Token: ...
     * </pre>
     *
     * <p>
     * Przykładowy request body:
     * </p>
     *
     * <pre>
     * {
     *   "reason": "phishing campaign"
     * }
     * </pre>
     *
     * <p>
     * Metoda wykonuje następujący przepływ:
     * </p>
     *
     * <ol>
     *     <li>Pobiera token administratora z nagłówka {@code X-Admin-Token}.</li>
     *     <li>Pobiera {@code shortCode} ze ścieżki URL.</li>
     *     <li>Waliduje request body {@link BlockUrlRequest}.</li>
     *     <li>Weryfikuje token administratora przez {@link AdminAuthService}.</li>
     *     <li>Wywołuje {@link ShortUrlService#block(String, String)}.</li>
     *     <li>Zwraca aktualne szczegóły skróconego URL-a po blokadzie.</li>
     * </ol>
     *
     * <p>
     * Po zablokowaniu link powinien przestać przekierowywać użytkowników
     * na docelowy adres. Typowo jego status zostaje ustawiony na {@code BLOCKED},
     * a cache powinien zostać unieważniony w warstwie serwisowej.
     * </p>
     *
     * <p>
     * Uwaga: ten endpoint nie powinien być publicznie dostępny bez dodatkowych
     * zabezpieczeń infrastrukturalnych, takich jak WAF, VPN, allowlista IP
     * albo pełniejszy mechanizm IAM.
     * </p>
     *
     * @param adminToken token administratora pobrany z nagłówka {@code X-Admin-Token};
     *                   parametr jest opcjonalny na poziomie Springa, ale brak tokena
     *                   powinien zostać odrzucony przez {@link AdminAuthService}
     * @param shortCode kod skróconego linku, który ma zostać zablokowany
     * @param request request body zawierający powód blokady
     * @return szczegóły URL-a po wykonaniu blokady
     */
    @PostMapping("/{shortCode}/block")
    public UrlDetailsResponse block(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @PathVariable String shortCode,
            @Valid @RequestBody BlockUrlRequest request
    ) {
        /*
         * Weryfikujemy, czy request pochodzi od administratora.
         *
         * Jeśli token jest niepoprawny lub go brakuje, requireAdmin()
         * powinno przerwać wykonanie metody przez rzucenie wyjątku.
         */
        adminAuthService.requireAdmin(adminToken);

        /*
         * Delegujemy właściwą logikę blokowania do ShortUrlService.
         *
         * Kontroler nie powinien samodzielnie zmieniać statusu encji ani
         * operować na repozytoriach. To odpowiedzialność warstwy serwisowej.
         */
        return shortUrlService.block(shortCode, request.reason());
    }

    /**
     * Odblokowuje wskazany short code.
     *
     * <p>
     * Endpoint:
     * </p>
     *
     * <pre>
     * POST /api/v1/admin/urls/{shortCode}/unblock
     * </pre>
     *
     * <p>
     * Wymagany nagłówek:
     * </p>
     *
     * <pre>
     * X-Admin-Token: ...
     * </pre>
     *
     * <p>
     * Metoda wykonuje następujący przepływ:
     * </p>
     *
     * <ol>
     *     <li>Pobiera token administratora z nagłówka {@code X-Admin-Token}.</li>
     *     <li>Pobiera {@code shortCode} ze ścieżki URL.</li>
     *     <li>Weryfikuje token administratora.</li>
     *     <li>Wywołuje {@link ShortUrlService#unblock(String)}.</li>
     *     <li>Zwraca aktualne szczegóły URL-a po odblokowaniu.</li>
     * </ol>
     *
     * <p>
     * Odblokowanie powinno przywrócić link do statusu aktywnego, o ile nie jest
     * wygasły, usunięty lub zablokowany z innego powodu biznesowego. Szczegóły
     * tej reguły powinny znajdować się w {@link ShortUrlService}.
     * </p>
     *
     * @param adminToken token administratora pobrany z nagłówka {@code X-Admin-Token}
     * @param shortCode kod skróconego linku, który ma zostać odblokowany
     * @return szczegóły URL-a po odblokowaniu
     */
    @PostMapping("/{shortCode}/unblock")
    public UrlDetailsResponse unblock(
            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
            @PathVariable String shortCode
    ) {
        /*
         * Tak samo jak w przypadku blokowania, najpierw weryfikujemy token.
         *
         * Nie wolno dopuścić do sytuacji, w której użytkownik bez uprawnień
         * może odblokować link oznaczony jako phishing, malware albo spam.
         */
        adminAuthService.requireAdmin(adminToken);

        /*
         * Delegujemy odblokowanie do warstwy serwisowej.
         *
         * ShortUrlService powinien zadbać o zmianę statusu oraz ewentualne
         * odświeżenie lub unieważnienie cache.
         */
        return shortUrlService.unblock(shortCode);
    }
}