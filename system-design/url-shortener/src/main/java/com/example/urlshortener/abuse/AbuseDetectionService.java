package com.example.urlshortener.abuse;

import com.example.urlshortener.queue.ClickMessage;
import com.example.urlshortener.service.ShortUrlCacheService;
import com.example.urlshortener.service.ShortUrlService;

import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Serwis odpowiedzialny za analizę kliknięć pod kątem potencjalnych nadużyć.
 *
 * <p>
 * Klasa jest częścią modułu abuse detection. Jej zadaniem jest ocena pojedynczego
 * zdarzenia kliknięcia, reprezentowanego przez {@link ClickMessage}, oraz zwrócenie
 * werdyktu w postaci {@link AbuseVerdict}.
 * </p>
 *
 * <p>
 * Serwis wykrywa między innymi:
 * </p>
 *
 * <ul>
 *     <li>brak nagłówka User-Agent,</li>
 *     <li>podejrzane User-Agenty, np. narzędzia skanujące,</li>
 *     <li>ruch z zablokowanych domen refererów,</li>
 *     <li>zbyt wiele kliknięć z tego samego IP w ten sam short code,</li>
 *     <li>dużą liczbę podejrzanych eventów dla jednego short code.</li>
 * </ul>
 *
 * <p>
 * Klasa wykorzystuje Redis do liczników czasowych. Dzięki temu może sprawdzać,
 * czy w określonym oknie czasowym doszło do przekroczenia progów nadużyć.
 * </p>
 *
 * <p>
 * Ważne: Redis jest tutaj traktowany jako mechanizm pomocniczy. Jeśli Redis jest
 * niedostępny, serwis nie blokuje przetwarzania kliknięcia, tylko loguje problem
 * na poziomie debug i traktuje dany licznik jako nieprzekroczony.
 * </p>
 */
@Service
public class AbuseDetectionService {

    /**
     * Logger używany do zapisywania informacji diagnostycznych.
     *
     * <p>
     * W tej klasie logger jest używany przede wszystkim do:
     * </p>
     *
     * <ul>
     *     <li>informowania o automatycznym zablokowaniu short code,</li>
     *     <li>informowania o błędzie podczas auto-blockingu,</li>
     *     <li>diagnostyki problemów z Redisem.</li>
     * </ul>
     */
    private static final Logger log = LoggerFactory.getLogger(AbuseDetectionService.class);

    /**
     * Konfiguracja modułu abuse detection.
     *
     * <p>
     * Zawiera między innymi:
     * </p>
     *
     * <ul>
     *     <li>informację, czy abuse detection jest włączone,</li>
     *     <li>limity kliknięć z jednego IP,</li>
     *     <li>okna czasowe dla liczników Redis,</li>
     *     <li>próg auto-blockingu,</li>
     *     <li>listę zablokowanych domen refererów,</li>
     *     <li>listę podejrzanych tokenów User-Agent.</li>
     * </ul>
     */
    private final AbuseDetectionProperties properties;

    /**
     * Redis template służący do operacji na licznikach tekstowych.
     *
     * <p>
     * W tej klasie Redis jest używany głównie do wykonywania atomowego
     * {@code INCR} na kluczach reprezentujących:
     * </p>
     *
     * <ul>
     *     <li>liczbę kliknięć z danego IP w konkretny short code,</li>
     *     <li>liczbę podejrzanych eventów dla konkretnego short code.</li>
     * </ul>
     *
     * <p>
     * Po pierwszym zwiększeniu licznika ustawiane jest jego TTL. Dzięki temu
     * licznik sam wygasa po zakończeniu okna czasowego.
     * </p>
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * Komponent klasyfikujący User-Agent.
     *
     * <p>
     * Na podstawie wartości nagłówka User-Agent zwraca uproszczone informacje,
     * takie jak typ urządzenia oraz przeglądarka. Te dane są potem używane
     * w analytics, niezależnie od tego, czy kliknięcie zostało uznane za podejrzane.
     * </p>
     */
    private final UserAgentClassifier userAgentClassifier;

    /**
     * Serwis domenowy do obsługi skróconych linków.
     *
     * <p>
     * W tej klasie jest używany do automatycznego blokowania short code,
     * jeżeli liczba podejrzanych eventów przekroczy skonfigurowany próg.
     * </p>
     */
    private final ShortUrlService shortUrlService;

    /**
     * Serwis cache dla skróconych linków.
     *
     * <p>
     * Po automatycznym zablokowaniu linku cache musi zostać wyczyszczony.
     * Bez tego Redis mógłby nadal przechowywać aktywne przekierowanie,
     * mimo że link w bazie został już oznaczony jako zablokowany.
     * </p>
     */
    private final ShortUrlCacheService cacheService;

    /**
     * Konstruktor z dependency injection.
     *
     * <p>
     * Wszystkie zależności są przekazywane przez konstruktor, co jest preferowanym
     * podejściem w Springu. Ułatwia to testowanie klasy oraz czyni zależności
     * jawnie widocznymi.
     * </p>
     */
    public AbuseDetectionService(
            AbuseDetectionProperties properties,
            StringRedisTemplate redisTemplate,
            UserAgentClassifier userAgentClassifier,
            ShortUrlService shortUrlService,
            ShortUrlCacheService cacheService
    ) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.userAgentClassifier = userAgentClassifier;
        this.shortUrlService = shortUrlService;
        this.cacheService = cacheService;
    }

    /**
     * Główna metoda serwisu.
     *
     * <p>
     * Ocenia pojedyncze kliknięcie i zwraca werdykt:
     * </p>
     *
     * <ul>
     *     <li>{@code clean} — kliknięcie nie wygląda podejrzanie,</li>
     *     <li>{@code suspicious} — kliknięcie zostało oznaczone jako podejrzane,</li>
     *     <li>{@code suspicious + autoBlocked} — kliknięcie było częścią wzorca,
     *     który doprowadził do automatycznego zablokowania short code.</li>
     * </ul>
     *
     * <p>
     * Metoda wykonuje następujące kroki:
     * </p>
     *
     * <ol>
     *     <li>Klasyfikuje User-Agent na typ urządzenia i przeglądarkę.</li>
     *     <li>Jeżeli abuse detection jest wyłączone, od razu zwraca czysty werdykt.</li>
     *     <li>Sprawdza pierwszą podejrzaną przyczynę, np. brak User-Agent albo zły referer.</li>
     *     <li>Sprawdza, czy z tego samego IP nie było zbyt wielu kliknięć w ten sam short code.</li>
     *     <li>Jeżeli kliknięcie jest podejrzane, zwiększa licznik podejrzanych eventów dla short code.</li>
     *     <li>Jeżeli próg auto-blockingu został przekroczony, blokuje link.</li>
     *     <li>Zwraca końcowy werdykt.</li>
     * </ol>
     *
     * @param message zdarzenie kliknięcia pobrane z kolejki
     * @return werdykt abuse detection dla danego kliknięcia
     */
    public AbuseVerdict evaluate(ClickMessage message) {

        /*
         * Klasyfikacja User-Agent jest wykonywana niezależnie od tego,
         * czy abuse detection jest włączone.
         *
         * Powód: typ urządzenia i przeglądarka mogą być używane w analytics,
         * nawet jeśli sam mechanizm wykrywania nadużyć jest wyłączony.
         */
        String deviceType = userAgentClassifier.deviceType(message.userAgent());
        String browser = userAgentClassifier.browser(message.userAgent());

        /*
         * Jeśli moduł abuse detection jest wyłączony w konfiguracji,
         * nie wykonujemy żadnych dalszych sprawdzeń.
         *
         * Zwracamy werdykt clean, ale nadal dołączamy deviceType i browser,
         * żeby downstream analytics mogło z nich skorzystać.
         */
        if (!properties.enabled()) {
            return AbuseVerdict.clean(deviceType, browser);
        }

        /*
         * Sprawdzamy pierwszą podejrzaną przyczynę na podstawie danych samego requestu:
         *
         * - brak User-Agent,
         * - User-Agent zawierający podejrzany token,
         * - referrer pochodzący z zablokowanej domeny.
         *
         * Jeśli metoda zwróci null, oznacza to, że na tym etapie nie znaleziono
         * jednoznacznej podejrzanej przyczyny.
         */
        String reason = firstSuspiciousReason(message);

        /*
         * Flaga suspicious określa, czy kliknięcie zostało uznane za podejrzane.
         *
         * Na tym etapie kliknięcie jest podejrzane tylko wtedy, gdy istnieje
         * konkretna przyczyna zwrócona przez firstSuspiciousReason().
         */
        boolean suspicious = reason != null;

        /*
         * Sprawdzamy licznik kliknięć z tego samego IP w ten sam short code.
         *
         * Jeżeli liczba kliknięć przekroczy skonfigurowany limit, kliknięcie
         * zostanie oznaczone jako podejrzane, nawet jeśli User-Agent i referrer
         * wyglądały normalnie.
         */
        if (tooManyClicksFromSameIp(message)) {
            suspicious = true;
            reason = appendReason(reason, "high_click_rate_from_same_ip");
        }

        /*
         * Flaga informująca, czy dany short code powinien zostać automatycznie
         * zablokowany.
         *
         * Auto-blocking jest rozważany tylko wtedy, gdy aktualne kliknięcie
         * jest już podejrzane.
         */
        boolean shouldAutoBlock = false;

        /*
         * Jeśli kliknięcie jest podejrzane, zwiększamy licznik podejrzanych eventów
         * dla danego short code.
         *
         * Jeżeli liczba podejrzanych eventów przekroczy próg z konfiguracji,
         * system oznaczy link do automatycznej blokady.
         */
        if (suspicious && tooManySuspiciousEventsForShortCode(message.shortCode())) {
            shouldAutoBlock = true;
            reason = appendReason(reason, "auto_block_threshold_exceeded");
        }

        /*
         * Jeśli przekroczono próg auto-blockingu, próbujemy zablokować short code.
         *
         * Operacja jest opakowana w try-catch, ponieważ błąd blokowania nie powinien
         * zatrzymać całego przetwarzania kliknięcia. Kliknięcie nadal może zostać
         * zapisane w analytics jako podejrzane.
         */
        if (shouldAutoBlock) {
            try {
                /*
                 * Oznaczamy link jako zablokowany w głównym serwisie domenowym.
                 *
                 * Komunikat zawiera przyczynę auto-blockingu, co ułatwia późniejszy
                 * audyt i diagnostykę w panelu administracyjnym.
                 */
                shortUrlService.block(message.shortCode(), "auto abuse detection: " + reason);

                /*
                 * Po blokadzie natychmiast usuwamy wpis z cache.
                 *
                 * Jest to bardzo ważne, ponieważ bez invalidacji cache zablokowany
                 * link mógłby nadal działać do czasu wygaśnięcia starego wpisu Redis.
                 */
                cacheService.evict(message.shortCode());

                /*
                 * Log ostrzegawczy informujący, że system automatycznie zablokował link.
                 */
                log.warn("Auto-blocked shortCode={} reason={}", message.shortCode(), reason);
            } catch (Exception exception) {
                /*
                 * Jeśli auto-blocking się nie powiedzie, logujemy problem.
                 *
                 * Nie rzucamy wyjątku dalej, ponieważ abuse detection działa w ścieżce
                 * asynchronicznej i nie powinno przerywać przetwarzania całego eventu.
                 */
                log.warn("Failed to auto-block shortCode={} reason={}", message.shortCode(), reason, exception);
            }
        }

        /*
         * Zwracamy końcowy werdykt.
         *
         * Jeśli kliknięcie było podejrzane, zwracamy reason, informację o auto-blockingu
         * oraz dane z klasyfikacji User-Agent.
         *
         * Jeśli kliknięcie było czyste, zwracamy clean verdict z informacją o urządzeniu
         * i przeglądarce.
         */
        return suspicious
                ? AbuseVerdict.suspicious(reason, shouldAutoBlock, deviceType, browser)
                : AbuseVerdict.clean(deviceType, browser);
    }

    /**
     * Sprawdza pierwszą podejrzaną przyczynę wynikającą bezpośrednio z danych requestu.
     *
     * <p>
     * Metoda sprawdza kolejno:
     * </p>
     *
     * <ol>
     *     <li>czy User-Agent jest pusty,</li>
     *     <li>czy User-Agent zawiera podejrzany token,</li>
     *     <li>czy referrer pochodzi z zablokowanej domeny.</li>
     * </ol>
     *
     * <p>
     * Zwraca pierwszą znalezioną przyczynę. Jeśli żadna reguła nie pasuje,
     * zwraca {@code null}.
     * </p>
     *
     * @param message zdarzenie kliknięcia
     * @return tekstowa przyczyna podejrzanego ruchu albo {@code null}
     */
    private String firstSuspiciousReason(ClickMessage message) {

        /*
         * Normalizujemy User-Agent do lowercase, żeby porównania tokenów były
         * case-insensitive.
         *
         * Jeśli User-Agent jest nullem, traktujemy go jak pusty string.
         */
        String userAgent = message.userAgent() == null
                ? ""
                : message.userAgent().toLowerCase(Locale.ROOT);

        /*
         * Brak User-Agent jest traktowany jako podejrzany.
         *
         * Zwykłe przeglądarki prawie zawsze wysyłają User-Agent. Brak tego nagłówka
         * często oznacza prosty bot, skrypt, skaner albo ręcznie przygotowany request.
         */
        if (userAgent.isBlank()) {
            return "missing_user_agent";
        }

        /*
         * Sprawdzamy, czy User-Agent zawiera jeden z podejrzanych tokenów.
         *
         * Lista tokenów pochodzi z konfiguracji:
         *
         * app.abuse.suspicious-user-agent-tokens
         *
         * Przykłady:
         * - sqlmap,
         * - nikto,
         * - masscan,
         * - python-requests,
         * - go-http-client.
         */
        for (String token : properties.suspiciousUserAgentTokens()) {
            if (!token.isBlank() && userAgent.contains(token.toLowerCase(Locale.ROOT))) {
                return "suspicious_user_agent:" + token;
            }
        }

        /*
         * Wyciągamy domenę z nagłówka referrer.
         *
         * Jeśli referrer jest pusty, niepoprawny albo nie zawiera hosta,
         * metoda referrerDomain() zwróci null.
         */
        String domain = referrerDomain(message.referrer());

        /*
         * Jeśli udało się ustalić domenę referera, sprawdzamy ją względem listy
         * zablokowanych domen.
         *
         * Użycie endsWith() pozwala blokować również subdomeny.
         *
         * Przykład:
         * blockedDomain = "spam.example"
         *
         * Dopasuje:
         * - spam.example
         * - sub.spam.example
         */
        if (domain != null) {
            for (String blockedDomain : properties.blockedReferrerDomains()) {
                if (!blockedDomain.isBlank() && domain.endsWith(blockedDomain.toLowerCase(Locale.ROOT))) {
                    return "blocked_referrer_domain:" + blockedDomain;
                }
            }
        }

        /*
         * Brak podejrzanej przyczyny na podstawie User-Agent i referrera.
         */
        return null;
    }

    /**
     * Sprawdza, czy z tego samego IP wykonano zbyt wiele kliknięć
     * w ten sam short code w skonfigurowanym oknie czasowym.
     *
     * <p>
     * Mechanizm wykorzystuje Redis jako licznik:
     * </p>
     *
     * <pre>
     * abuse:ip:{shortCode}:{sha256(ip)}
     * </pre>
     *
     * <p>
     * IP jest hashowane, aby nie zapisywać surowego adresu IP w kluczu Redis.
     * To nie daje pełnej anonimizacji, ale jest lepsze niż trzymanie IP jawnie.
     * </p>
     *
     * @param message zdarzenie kliknięcia
     * @return {@code true}, jeśli limit kliknięć został przekroczony
     */
    private boolean tooManyClicksFromSameIp(ClickMessage message) {

        /*
         * Jeśli nie mamy adresu IP, nie możemy policzyć limitu per IP.
         *
         * W takiej sytuacji metoda zwraca false. Sam brak IP nie jest tutaj
         * traktowany jako abuse — ewentualnie może być obsłużony przez inną regułę.
         */
        if (message.ipAddress() == null || message.ipAddress().isBlank()) {
            return false;
        }

        try {
            /*
             * Budujemy klucz Redis.
             *
             * Klucz zawiera:
             * - prefiks abuse:ip,
             * - shortCode,
             * - hash SHA-256 adresu IP.
             *
             * Dzięki temu licznik jest osobny dla każdej pary:
             *
             * shortCode + IP
             */
            String key = "abuse:ip:" + message.shortCode() + ":" + sha256(message.ipAddress());

            /*
             * Atomowo zwiększamy licznik w Redisie.
             *
             * INCR jest operacją atomową, więc działa poprawnie również przy wielu
             * równoległych requestach.
             */
            Long value = redisTemplate.opsForValue().increment(key);

            /*
             * Jeśli licznik został utworzony właśnie teraz, ustawiamy jego TTL.
             *
             * TTL odpowiada oknu czasowemu z konfiguracji, np. 5 minut.
             *
             * Po upływie TTL licznik zniknie z Redisa, a kolejne kliknięcia zaczną
             * nowe okno pomiarowe.
             */
            if (value != null && value == 1L) {
                redisTemplate.expire(key, properties.ipWindow());
            }

            /*
             * Jeśli licznik przekroczył skonfigurowany próg, uznajemy ruch
             * za podejrzany.
             */
            return value != null && value > properties.suspiciousClicksPerIpPerShortCode();
        } catch (Exception exception) {
            /*
             * Redis jest pomocniczym komponentem abuse detection.
             *
             * Jeśli Redis jest niedostępny, nie blokujemy przetwarzania eventu.
             * Zwracamy false, czyli zachowujemy się tak, jakby limit nie został
             * przekroczony.
             */
            log.debug("Redis unavailable for IP abuse detection", exception);
            return false;
        }
    }

    /**
     * Sprawdza, czy liczba podejrzanych eventów dla jednego short code
     * przekroczyła próg auto-blockingu.
     *
     * <p>
     * Ta metoda jest wywoływana tylko wtedy, gdy aktualne kliknięcie zostało
     * wcześniej uznane za podejrzane.
     * </p>
     *
     * <p>
     * Redis key:
     * </p>
     *
     * <pre>
     * abuse:short:{shortCode}
     * </pre>
     *
     * @param shortCode kod skróconego linku
     * @return {@code true}, jeśli należy rozważyć automatyczną blokadę linku
     */
    private boolean tooManySuspiciousEventsForShortCode(String shortCode) {
        try {
            /*
             * Jeden licznik na jeden short code.
             *
             * Licznik zwiększa się tylko dla eventów, które zostały wcześniej
             * oznaczone jako podejrzane.
             */
            String key = "abuse:short:" + shortCode;

            /*
             * Atomowo zwiększamy licznik podejrzanych eventów dla short code.
             */
            Long value = redisTemplate.opsForValue().increment(key);

            /*
             * Dla nowego licznika ustawiamy TTL zgodny z shortCodeWindow.
             *
             * Przykład:
             * jeśli shortCodeWindow = 15 minut, licznik dotyczy podejrzanych
             * eventów z ostatniego piętnastominutowego okna.
             */
            if (value != null && value == 1L) {
                redisTemplate.expire(key, properties.shortCodeWindow());
            }

            /*
             * Jeśli licznik przekroczył próg autoBlockSuspiciousEvents,
             * metoda zwraca true.
             *
             * Wtedy evaluate() spróbuje zablokować dany short code.
             */
            return value != null && value > properties.autoBlockSuspiciousEvents();
        } catch (Exception exception) {
            /*
             * Jeśli Redis jest niedostępny, nie wykonujemy auto-blockingu
             * na podstawie tego licznika.
             *
             * To bezpieczniejsze niż przypadkowe blokowanie linków na podstawie
             * niepełnych danych.
             */
            log.debug("Redis unavailable for short-code abuse detection", exception);
            return false;
        }
    }

    /**
     * Wyciąga domenę hosta z wartości nagłówka Referrer.
     *
     * <p>
     * Przykład:
     * </p>
     *
     * <pre>
     * https://sub.example.com/path?q=1 -> sub.example.com
     * </pre>
     *
     * <p>
     * Jeśli referrer jest pusty, niepoprawny albo nie zawiera hosta,
     * metoda zwraca {@code null}.
     * </p>
     *
     * @param referrer wartość nagłówka HTTP Referer/Referrer
     * @return domena referera w lowercase albo {@code null}
     */
    private String referrerDomain(String referrer) {

        /*
         * Brak referrera nie jest sam w sobie traktowany jako podejrzany.
         *
         * Wiele normalnych requestów może nie mieć referrera, np. wejście
         * bezpośrednie, aplikacje mobilne, komunikatory albo przeglądarki
         * z restrykcyjną polityką prywatności.
         */
        if (referrer == null || referrer.isBlank()) {
            return null;
        }

        try {
            /*
             * URI.create() parsuje string jako URI.
             *
             * Jeśli string nie jest poprawnym URI, rzuci wyjątek
             * IllegalArgumentException, który łapiemy poniżej.
             */
            String host = URI.create(referrer).getHost();

            /*
             * Host normalizujemy do lowercase, aby porównania domen były
             * case-insensitive.
             */
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            /*
             * Niepoprawny referrer ignorujemy.
             *
             * Ta metoda nie powinna przerywać procesu analytics ani abuse detection
             * tylko dlatego, że nagłówek referrer był źle sformatowany.
             */
            return null;
        }
    }

    /**
     * Dokleja kolejną przyczynę do istniejącego tekstu reason.
     *
     * <p>
     * Przyczyny są łączone przecinkami, np.:
     * </p>
     *
     * <pre>
     * suspicious_user_agent:sqlmap,high_click_rate_from_same_ip
     * </pre>
     *
     * <p>
     * Jeśli aktualna przyczyna jest pusta albo null, metoda zwraca po prostu
     * nową przyczynę.
     * </p>
     *
     * @param current aktualny tekst przyczyny
     * @param next kolejna przyczyna do dodania
     * @return połączony tekst przyczyn
     */
    private String appendReason(String current, String next) {
        return current == null || current.isBlank()
                ? next
                : current + "," + next;
    }

    /**
     * Oblicza hash SHA-256 dla podanej wartości i zwraca go jako tekst hex.
     *
     * <p>
     * W tej klasie metoda jest używana do hashowania adresu IP przed zapisaniem
     * go w kluczu Redis.
     * </p>
     *
     * <p>
     * Przykład użycia:
     * </p>
     *
     * <pre>
     * 192.168.1.10 -> sha256 -> "długi_string_hex"
     * </pre>
     *
     * <p>
     * To ogranicza ekspozycję surowych adresów IP w Redisie. Nie jest to jednak
     * pełna anonimizacja, ponieważ dla znanych adresów IP hash może być odtworzony
     * metodą słownikową. W produkcyjnym systemie warto rozważyć HMAC z sekretnym
     * kluczem albo rotowany salt.
     * </p>
     *
     * @param value wartość wejściowa do zahashowania
     * @return hash SHA-256 w formacie hex
     * @throws Exception jeśli algorytm SHA-256 byłby niedostępny
     */
    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes())
        );
    }
}