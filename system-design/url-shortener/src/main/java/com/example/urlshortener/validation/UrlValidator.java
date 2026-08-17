package com.example.urlshortener.validation;

import com.example.urlshortener.exception.InvalidUrlException;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Komponent odpowiedzialny za walidację URL-i przekazywanych do systemu.
 *
 * <p>
 * Ta klasa jest używana podczas tworzenia skróconego URL-a. Jej zadaniem jest
 * sprawdzenie, czy podany przez użytkownika adres może być bezpiecznie zapisany
 * jako docelowy adres przekierowania.
 * </p>
 *
 * <p>
 * Walidator dopuszcza tylko publiczne URL-e HTTP/HTTPS. Odrzuca między innymi:
 * </p>
 *
 * <ul>
 *     <li>URL-e bez schematu, np. {@code example.com},</li>
 *     <li>schematy inne niż {@code http} i {@code https}, np. {@code file:}, {@code ftp:}, {@code javascript:},</li>
 *     <li>adresy wskazujące na localhost,</li>
 *     <li>adresy prywatne i loopback, np. {@code 127.0.0.1}, {@code 10.0.0.1}, {@code 192.168.1.1},</li>
 *     <li>adresy link-local i multicast,</li>
 *     <li>URL-e zawierające user info, np. {@code https://user:pass@example.com}.</li>
 * </ul>
 *
 * <p>
 * Głównym celem tej klasy jest ograniczenie ryzyka nadużyć, szczególnie:
 * </p>
 *
 * <ul>
 *     <li>SSRF, czyli Server-Side Request Forgery,</li>
 *     <li>ukrywania linków do lokalnych lub prywatnych zasobów,</li>
 *     <li>tworzenia mylących URL-i z user info,</li>
 *     <li>przekierowań do nieobsługiwanych albo niebezpiecznych schematów.</li>
 * </ul>
 *
 * <p>
 * Ważna uwaga: ta klasa wykonuje podstawową walidację techniczną i bezpieczeństwa,
 * ale nie zastępuje pełnego systemu reputacji domen, Safe Browsing, blocklist,
 * antyphishingu ani mechanizmów abuse detection.
 * </p>
 */
@Component
public class UrlValidator {

    /**
     * Waliduje URL jako publiczny adres HTTP/HTTPS.
     *
     * <p>
     * Metoda wykonuje następujące kroki:
     * </p>
     *
     * <ol>
     *     <li>Parsuje surowy tekst do obiektu {@link URI}.</li>
     *     <li>Sprawdza, czy URL ma schemat.</li>
     *     <li>Akceptuje wyłącznie schematy {@code http} i {@code https}.</li>
     *     <li>Sprawdza, czy URL ma poprawny host.</li>
     *     <li>Normalizuje domeny międzynarodowe przez {@link IDN#toASCII(String)}.</li>
     *     <li>Blokuje localhost.</li>
     *     <li>Blokuje adresy prywatne, loopback, link-local i multicast.</li>
     *     <li>Blokuje URL-e zawierające user info.</li>
     *     <li>Zwraca poprawny, znormalizowany obiekt {@link URI}.</li>
     * </ol>
     *
     * <p>
     * Jeśli URL nie przejdzie walidacji, metoda rzuca {@link InvalidUrlException}
     * z komunikatem opisującym problem.
     * </p>
     *
     * @param rawUrl surowy URL przekazany przez użytkownika
     * @return poprawny, znormalizowany URI
     * @throws InvalidUrlException jeśli URL jest niepoprawny lub niedozwolony
     */
    public URI validatePublicHttpUrl(String rawUrl) {

        /*
         * Najpierw parsujemy tekst do obiektu URI.
         *
         * URI jest wygodniejsze i bezpieczniejsze niż ręczne operowanie na stringu,
         * ponieważ pozwala odczytać poszczególne części adresu, np. scheme, host,
         * port, path, query.
         *
         * parse() wykonuje również normalize(), które upraszcza ścieżkę,
         * np. usuwa segmenty typu "." i rozwiązuje część segmentów "..".
         */
        URI uri = parse(rawUrl);

        /*
         * Pobieramy schemat URL-a.
         *
         * Schemat to część przed dwukropkiem, np.:
         *
         * https://example.com -> https
         * http://example.com  -> http
         * file:///tmp/a.txt   -> file
         */
        String scheme = uri.getScheme();

        /*
         * URL bez schematu jest odrzucany.
         *
         * Przykład odrzucony:
         *
         * example.com/path
         *
         * Taki adres jest niejednoznaczny i mógłby zostać różnie zinterpretowany
         * przez klienta HTTP albo przeglądarkę.
         */
        if (scheme == null) {
            throw new InvalidUrlException("URL must include a scheme: http or https");
        }

        /*
         * Normalizujemy schemat do lowercase, żeby porównanie było odporne
         * na wielkość liter.
         *
         * Przykład:
         * HTTPS zostanie potraktowane tak samo jak https.
         */
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);

        /*
         * Dopuszczamy wyłącznie http i https.
         *
         * Odrzucamy między innymi:
         *
         * - javascript:
         * - file:
         * - ftp:
         * - data:
         * - mailto:
         *
         * W systemie URL shortener redirect powinien prowadzić wyłącznie
         * do webowych adresów HTTP/HTTPS.
         */
        if (!normalizedScheme.equals("http") && !normalizedScheme.equals("https")) {
            throw new InvalidUrlException("Only http and https URLs are allowed");
        }

        /*
         * Pobieramy hosta z URI.
         *
         * Host to domena albo adres IP, np.:
         *
         * https://example.com/path -> example.com
         * http://127.0.0.1:8080    -> 127.0.0.1
         */
        String host = uri.getHost();

        /*
         * Jeśli host jest pusty lub nie istnieje, URL jest niepoprawny
         * dla naszego use-case'u.
         *
         * Przykład:
         *
         * https:///path
         */
        if (host == null || host.isBlank()) {
            throw new InvalidUrlException("URL must include a valid host");
        }

        /*
         * Konwertujemy host do formatu ASCII z użyciem IDN.toASCII().
         *
         * Jest to ważne dla domen międzynarodowych, np. zawierających polskie znaki
         * lub inne znaki Unicode. Taka domena może mieć reprezentację punycode.
         *
         * Przykład:
         *
         * żółć.pl -> xn--...
         *
         * Następnie normalizujemy host do lowercase.
         */
        String asciiHost = IDN.toASCII(host).toLowerCase(Locale.ROOT);

        /*
         * Blokujemy localhost.
         *
         * Przekierowania do localhosta nie mają sensu dla publicznego URL shortenera
         * i mogą być wykorzystane do nadużyć, testów SSRF lub mylących linków.
         *
         * Przykłady odrzucone:
         *
         * http://localhost:8080
         * http://api.localhost
         */
        if (isLocalhost(asciiHost)) {
            throw new InvalidUrlException("Localhost URLs are not allowed");
        }

        /*
         * Blokujemy adresy prywatne, loopback, link-local i multicast.
         *
         * Celem jest niedopuszczenie do tworzenia linków prowadzących do zasobów,
         * które nie powinny być publicznie dostępne, np.:
         *
         * - 127.0.0.1
         * - 10.0.0.1
         * - 192.168.1.1
         * - 169.254.x.x
         *
         * To ogranicza ryzyko SSRF i przypadkowego ujawniania wewnętrznych adresów.
         */
        if (isPrivateOrLoopbackAddress(asciiHost)) {
            throw new InvalidUrlException("Private, loopback, link-local and multicast addresses are not allowed");
        }

        /*
         * Blokujemy URL-e zawierające user info.
         *
         * User info to część przed znakiem @, np.:
         *
         * https://user:password@example.com
         *
         * Taki URL może być mylący dla użytkowników i bywa używany w phishingu.
         *
         * Przykład mylącego linku:
         *
         * https://trusted-bank.com@evil.example
         *
         * Realnym hostem jest evil.example, ale użytkownik może zauważyć
         * trusted-bank.com przed znakiem @.
         */
        if (uri.getRawUserInfo() != null) {
            throw new InvalidUrlException("URLs with user info are not allowed");
        }

        /*
         * Jeśli wszystkie warunki przeszły poprawnie, zwracamy znormalizowany URI.
         *
         * Ten URI może zostać zapisany jako longUrl w bazie danych.
         */
        return uri;
    }

    /**
     * Parsuje surowy string do obiektu URI i wykonuje podstawową normalizację.
     *
     * <p>
     * Jeśli string nie jest poprawnym URI, metoda rzuca {@link InvalidUrlException}.
     * </p>
     *
     * @param rawUrl surowy URL podany przez użytkownika
     * @return znormalizowany obiekt URI
     */
    private URI parse(String rawUrl) {
        try {
            /*
             * Tworzymy URI z surowego tekstu.
             *
             * Konstruktor URI sprawdza składnię adresu i rzuca URISyntaxException,
             * jeśli tekst nie spełnia reguł składni URI.
             */
            return new URI(rawUrl).normalize();
        } catch (URISyntaxException exception) {
            /*
             * Nie przepuszczamy wyjątku technicznego na zewnątrz.
             *
             * Zamiast tego rzucamy wyjątek domenowy aplikacji, który później może
             * zostać zamieniony przez GlobalExceptionHandler na odpowiedź HTTP 400.
             */
            throw new InvalidUrlException("URL is not syntactically valid");
        }
    }

    /**
     * Sprawdza, czy host wskazuje na localhost.
     *
     * <p>
     * Blokowane są:
     * </p>
     *
     * <ul>
     *     <li>{@code localhost},</li>
     *     <li>subdomeny kończące się na {@code .localhost}, np. {@code api.localhost}.</li>
     * </ul>
     *
     * @param host host w formacie lowercase ASCII
     * @return {@code true}, jeśli host jest localhostem albo subdomeną localhost
     */
    private boolean isLocalhost(String host) {
        return host.equals("localhost") || host.endsWith(".localhost");
    }

    /**
     * Sprawdza, czy host rozwiązuje się do adresu niedozwolonego dla publicznego redirectu.
     *
     * <p>
     * Metoda używa {@link InetAddress#getByName(String)}, czyli próbuje rozwiązać
     * host do adresu IP. Następnie sprawdza właściwości adresu.
     * </p>
     *
     * <p>
     * Blokowane są między innymi:
     * </p>
     *
     * <ul>
     *     <li>adresy any-local, np. {@code 0.0.0.0},</li>
     *     <li>adresy loopback, np. {@code 127.0.0.1}, {@code ::1},</li>
     *     <li>adresy link-local, np. {@code 169.254.x.x},</li>
     *     <li>adresy site-local, np. {@code 10.x.x.x}, {@code 172.16.x.x}, {@code 192.168.x.x},</li>
     *     <li>adresy multicast.</li>
     * </ul>
     *
     * <p>
     * Jeśli host nie da się rozwiązać albo wystąpi błąd DNS, metoda zwraca
     * {@code false}. Oznacza to, że sama niemożność rozwiązania hosta nie powoduje
     * odrzucenia URL-a na tym etapie.
     * </p>
     *
     * <p>
     * Ważne ograniczenie: ta metoda sprawdza adres w momencie walidacji.
     * W produkcyjnym systemie nadal istnieje ryzyko DNS rebindingu, czyli sytuacji,
     * w której domena początkowo wskazuje na publiczny adres IP, a później zaczyna
     * wskazywać na adres prywatny. Dlatego dla systemów wysokiego ryzyka warto
     * ponawiać walidację DNS przy redirectach albo stosować dodatkowe mechanizmy
     * bezpieczeństwa na poziomie egress/network.
     * </p>
     *
     * @param host host w formacie lowercase ASCII
     * @return {@code true}, jeśli host wskazuje na adres prywatny, lokalny,
     *         link-local, loopback albo multicast
     */
    private boolean isPrivateOrLoopbackAddress(String host) {
        try {
            /*
             * Rozwiązujemy host do InetAddress.
             *
             * Dla hostów będących literalnym IP, np. 127.0.0.1, nie wymaga to
             * normalnego zapytania DNS. Dla domen wykona się resolver DNS.
             */
            InetAddress address = InetAddress.getByName(host);

            /*
             * Sprawdzamy kilka kategorii adresów, których nie chcemy dopuścić
             * jako publicznych adresów docelowych.
             */
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception ignored) {
            /*
             * Jeśli wystąpi błąd podczas rozwiązywania hosta, nie traktujemy tego
             * jako adres prywatny.
             *
             * To jest świadoma decyzja projektowa, ale dość liberalna. W bardziej
             * restrykcyjnym systemie można byłoby odrzucać nierozwiązywalne hosty.
             */
            return false;
        }
    }
}