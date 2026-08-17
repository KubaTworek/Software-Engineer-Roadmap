package com.example.urlshortener.abuse;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Komponent odpowiedzialny za prostą klasyfikację nagłówka HTTP User-Agent.
 *
 * <p>
 * User-Agent to nagłówek wysyłany przez klienta HTTP, który zwykle zawiera
 * informacje o przeglądarce, systemie operacyjnym, urządzeniu albo narzędziu,
 * które wykonało request.
 * </p>
 *
 * <p>
 * Przykładowe wartości User-Agent:
 * </p>
 *
 * <pre>
 * Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36
 * Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Safari/604.1
 * curl/8.1.2
 * Googlebot/2.1
 * </pre>
 *
 * <p>
 * Ta klasa nie jest pełnoprawnym parserem User-Agentów. Jest to lekki,
 * heurystyczny klasyfikator, który rozpoznaje podstawowe typy urządzeń
 * oraz najpopularniejsze przeglądarki/narzędzia.
 * </p>
 *
 * <p>
 * Klasa może być używana między innymi przez:
 * </p>
 *
 * <ul>
 *     <li>analytics, do raportowania typu urządzenia i przeglądarki,</li>
 *     <li>abuse detection, do oznaczania botów i automatycznych klientów,</li>
 *     <li>dashboard, do agregowania kliknięć według device/browser.</li>
 * </ul>
 *
 * <p>
 * Ograniczenie: ponieważ klasyfikacja opiera się na prostym wyszukiwaniu
 * fragmentów tekstu, wynik nie zawsze będzie idealnie precyzyjny. User-Agent
 * może być sfałszowany, nietypowy albo zawierać wiele tokenów naraz.
 * </p>
 */
@Component
public class UserAgentClassifier {

    /**
     * Klasyfikuje typ urządzenia lub klienta na podstawie wartości User-Agent.
     *
     * <p>
     * Możliwe wartości zwracane przez metodę:
     * </p>
     *
     * <ul>
     *     <li>{@code unknown} — User-Agent jest pusty albo nie został podany,</li>
     *     <li>{@code bot} — User-Agent wygląda na bota, crawlera albo spidera,</li>
     *     <li>{@code mobile} — User-Agent wygląda na telefon lub klienta mobilnego,</li>
     *     <li>{@code tablet} — User-Agent wygląda na tablet,</li>
     *     <li>{@code desktop} — domyślna klasyfikacja dla pozostałych klientów.</li>
     * </ul>
     *
     * <p>
     * Kolejność sprawdzeń ma znaczenie:
     * </p>
     *
     * <ol>
     *     <li>Najpierw obsługiwany jest brak User-Agent.</li>
     *     <li>Następnie sprawdzane są boty/crawlery.</li>
     *     <li>Potem urządzenia mobilne.</li>
     *     <li>Potem tablety.</li>
     *     <li>Na końcu metoda zwraca {@code desktop} jako fallback.</li>
     * </ol>
     *
     * <p>
     * Przykład: jeśli User-Agent zawiera słowo {@code Googlebot}, metoda zwróci
     * {@code bot}. Jeśli zawiera {@code iPhone}, zwróci {@code mobile}.
     * </p>
     *
     * @param userAgent wartość nagłówka HTTP User-Agent
     * @return uproszczony typ urządzenia albo klienta
     */
    public String deviceType(String userAgent) {

        /*
         * Jeśli User-Agent nie został podany albo jest pusty, nie da się
         * wiarygodnie określić typu urządzenia.
         *
         * Zwracamy "unknown", zamiast zakładać np. desktop.
         */
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }

        /*
         * Normalizujemy User-Agent do lowercase.
         *
         * Dzięki temu późniejsze sprawdzenia są niewrażliwe na wielkość liter.
         * Przykład:
         *
         * "GoogleBot" i "googlebot" będą traktowane tak samo.
         */
        String ua = userAgent.toLowerCase(Locale.ROOT);

        /*
         * Sprawdzenie botów, crawlerów i spiderów.
         *
         * Te tokeny często występują w User-Agentach automatycznych klientów,
         * np. robotów wyszukiwarek albo crawlerów.
         *
         * Przykłady:
         * - Googlebot
         * - Bingbot
         * - crawler
         * - spider
         */
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) {
            return "bot";
        }

        /*
         * Sprawdzenie urządzeń mobilnych.
         *
         * Tokeny:
         * - mobile,
         * - android,
         * - iphone
         *
         * zwykle oznaczają telefon lub mobilną przeglądarkę.
         *
         * Uwaga: niektóre tablety z Androidem też mogą zawierać token "android",
         * dlatego taka heurystyka nie jest w 100% precyzyjna.
         */
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) {
            return "mobile";
        }

        /*
         * Sprawdzenie tabletów.
         *
         * Tokeny:
         * - ipad,
         * - tablet
         *
         * wskazują najczęściej na tablet.
         *
         * Uwaga: obecna kolejność sprawdzeń powoduje, że User-Agent zawierający
         * jednocześnie np. "android" i "tablet" zostanie wcześniej sklasyfikowany
         * jako "mobile". Jeśli zależy Ci na dokładniejszym wykrywaniu tabletów,
         * warto sprawdzenie tabletu przenieść przed sprawdzenie mobile.
         */
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "tablet";
        }

        /*
         * Fallback.
         *
         * Jeśli User-Agent nie wygląda na bota, mobile ani tablet,
         * traktujemy go jako desktop.
         *
         * To uproszczenie jest wystarczające dla podstawowych statystyk,
         * ale nie zastępuje pełnego parsera User-Agentów.
         */
        return "desktop";
    }

    /**
     * Klasyfikuje przeglądarkę lub klienta HTTP na podstawie User-Agent.
     *
     * <p>
     * Możliwe wartości zwracane przez metodę:
     * </p>
     *
     * <ul>
     *     <li>{@code unknown} — User-Agent jest pusty albo nie został podany,</li>
     *     <li>{@code edge} — Microsoft Edge,</li>
     *     <li>{@code chrome} — Google Chrome,</li>
     *     <li>{@code firefox} — Mozilla Firefox,</li>
     *     <li>{@code safari} — Apple Safari,</li>
     *     <li>{@code curl} — klient CLI curl,</li>
     *     <li>{@code bot} — bot lub crawler,</li>
     *     <li>{@code other} — nierozpoznany klient.</li>
     * </ul>
     *
     * <p>
     * Kolejność sprawdzeń jest ważna, ponieważ wiele przeglądarek zawiera
     * w User-Agentach tokeny innych silników lub przeglądarek.
     * </p>
     *
     * <p>
     * Przykład: Edge często zawiera tokeny podobne do Chrome, dlatego {@code edg/}
     * musi być sprawdzany przed {@code chrome/}.
     * </p>
     *
     * @param userAgent wartość nagłówka HTTP User-Agent
     * @return uproszczona nazwa przeglądarki lub klienta HTTP
     */
    public String browser(String userAgent) {

        /*
         * Jeśli User-Agent nie istnieje, nie da się określić przeglądarki.
         */
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }

        /*
         * Normalizacja do lowercase zapewnia case-insensitive matching.
         */
        String ua = userAgent.toLowerCase(Locale.ROOT);

        /*
         * Microsoft Edge.
         *
         * Nowy Edge oparty o Chromium zwykle zawiera token "Edg/".
         *
         * Sprawdzamy go przed Chrome, bo Edge często zawiera również tokeny
         * charakterystyczne dla Chrome/Chromium.
         */
        if (ua.contains("edg/")) {
            return "edge";
        }

        /*
         * Google Chrome.
         *
         * Warunek sprawdza obecność "chrome/" i jednocześnie wyklucza "chromium".
         *
         * Jest to uproszczenie. W praktyce wiele przeglądarek opartych o Chromium
         * może zawierać token "chrome/", więc klasyfikacja może nie być idealna.
         */
        if (ua.contains("chrome/") && !ua.contains("chromium")) {
            return "chrome";
        }

        /*
         * Mozilla Firefox.
         *
         * Firefox zwykle zawiera token "firefox/".
         */
        if (ua.contains("firefox/")) {
            return "firefox";
        }

        /*
         * Apple Safari.
         *
         * Safari zwykle zawiera token "safari/", ale Chrome również często
         * zawiera "safari/" w swoim User-Agent.
         *
         * Dlatego wykluczamy "chrome/".
         *
         * Uwaga: dla lepszej precyzji można byłoby również wykluczyć "chromium"
         * oraz inne przeglądarki oparte o Chromium.
         */
        if (ua.contains("safari/") && !ua.contains("chrome/")) {
            return "safari";
        }

        /*
         * Klient HTTP curl.
         *
         * curl jest często używany w testach, skryptach, monitoringu lub prostych
         * automatyzacjach. Nie zawsze oznacza abuse, ale jest przydatny
         * do klasyfikacji analytics.
         */
        if (ua.contains("curl/")) {
            return "curl";
        }

        /*
         * Bot.
         *
         * Sprawdzenie "bot" jest tu po wykryciu popularnych przeglądarek.
         *
         * W praktyce część botów podszywa się pod przeglądarki, więc ta heurystyka
         * nie wykryje wszystkiego.
         */
        if (ua.contains("bot")) {
            return "bot";
        }

        /*
         * Fallback dla nierozpoznanych klientów.
         */
        return "other";
    }
}