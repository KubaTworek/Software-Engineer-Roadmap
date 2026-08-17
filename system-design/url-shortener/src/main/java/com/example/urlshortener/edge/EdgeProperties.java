package com.example.urlshortener.edge;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Klasa konfiguracyjna przechowująca ustawienia warstwy edge/CDN.
 *
 * <p>
 * Jest mapowana automatycznie przez Spring Boot na podstawie właściwości
 * zaczynających się od prefiksu:
 * </p>
 *
 * <pre>
 * app.edge
 * </pre>
 *
 * <p>
 * Przykład konfiguracji w {@code application.yml}:
 * </p>
 *
 * <pre>
 * app:
 *   edge:
 *     enabled: true
 *     internal-token: local-edge-token
 *     cache-control-for-redirects: public, max-age=60, stale-while-revalidate=300
 * </pre>
 *
 * <p>
 * Ta klasa jest używana głównie przez:
 * </p>
 *
 * <ul>
 *     <li>{@code RedirectController} — do ustawiania nagłówka {@code Cache-Control}
 *     na publicznych redirectach,</li>
 *     <li>{@code EdgeLookupController} — do sprawdzania, czy internal edge lookup
 *     jest włączony oraz czy request zawiera poprawny {@code X-Edge-Token}.</li>
 * </ul>
 *
 * <p>
 * Ustawienia edge są istotne w architekturze, w której przed aplikacją działa
 * CDN, edge worker albo inna warstwa pośrednia, np. Cloudflare Worker, Fastly
 * Compute lub Lambda@Edge.
 * </p>
 */
@ConfigurationProperties(prefix = "app.edge")
public class EdgeProperties {

    /**
     * Flaga włączająca lub wyłączająca funkcje edge.
     *
     * <p>
     * Jeśli {@code enabled = true}, endpointy wewnętrzne edge, np.
     * {@code /internal/edge/urls/{shortCode}}, mogą być używane przez zaufaną
     * warstwę CDN/edge.
     * </p>
     *
     * <p>
     * Jeśli {@code enabled = false}, kontroler edge powinien odrzucać requesty,
     * nawet jeśli zawierają poprawny token.
     * </p>
     *
     * <p>
     * Domyślnie funkcja jest włączona, co jest wygodne lokalnie i w środowisku
     * demonstracyjnym. W produkcji warto świadomie zdecydować, czy edge lookup
     * ma być aktywny.
     * </p>
     */
    private boolean enabled = true;

    /**
     * Wewnętrzny token używany do autoryzacji requestów z warstwy edge/CDN.
     *
     * <p>
     * Token powinien być przesyłany przez edge worker albo CDN w nagłówku:
     * </p>
     *
     * <pre>
     * X-Edge-Token: ...
     * </pre>
     *
     * <p>
     * Dzięki temu endpointy typu {@code /internal/edge/...} nie są dostępne
     * dla przypadkowych klientów bez znajomości tokenu.
     * </p>
     *
     * <p>
     * Domyślna wartość {@code local-edge-token} nadaje się tylko do lokalnego
     * developmentu. W produkcji token powinien pochodzić z bezpiecznego źródła,
     * np. secret managera albo zmiennej środowiskowej.
     * </p>
     */
    private String internalToken = "local-edge-token";

    /**
     * Wartość nagłówka {@code Cache-Control} ustawiana na odpowiedziach redirectu.
     *
     * <p>
     * Jest używana przez publiczny endpoint redirectu, np.:
     * </p>
     *
     * <pre>
     * GET /{shortCode}
     * </pre>
     *
     * <p>
     * Domyślna wartość:
     * </p>
     *
     * <pre>
     * public, max-age=60, stale-while-revalidate=300
     * </pre>
     *
     * <p>
     * oznacza:
     * </p>
     *
     * <ul>
     *     <li>{@code public} — odpowiedź może być cache'owana przez przeglądarkę,
     *     CDN albo proxy,</li>
     *     <li>{@code max-age=60} — odpowiedź jest świeża przez 60 sekund,</li>
     *     <li>{@code stale-while-revalidate=300} — cache może przez pewien czas
     *     zwracać starą odpowiedź podczas odświeżania jej w tle.</li>
     * </ul>
     *
     * <p>
     * To ustawienie poprawia wydajność i zmniejsza obciążenie backendu, ale ma
     * konsekwencje bezpieczeństwa. Jeśli link zostanie zablokowany jako phishing,
     * CDN może przez krótki czas nadal posiadać starą odpowiedź redirectu.
     * Dlatego w produkcji trzeba dobrać tę wartość ostrożnie albo zapewnić
     * mechanizm purge/invalidation cache po blokadzie linku.
     * </p>
     */
    private String cacheControlForRedirects = "public, max-age=60, stale-while-revalidate=300";

    /**
     * Zwraca informację, czy funkcje edge są włączone.
     *
     * @return {@code true}, jeśli edge lookup jest aktywny
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Ustawia flagę aktywności funkcji edge.
     *
     * <p>
     * Setter jest wymagany przez mechanizm {@link ConfigurationProperties},
     * ponieważ Spring Boot mapuje wartości z konfiguracji na pola klasy.
     * </p>
     *
     * @param enabled czy funkcje edge mają być włączone
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Zwraca token wewnętrzny używany do autoryzacji edge lookup.
     *
     * @return skonfigurowany token edge
     */
    public String getInternalToken() {
        return internalToken;
    }

    /**
     * Ustawia token wewnętrzny używany przez edge/CDN.
     *
     * @param internalToken token wymagany w nagłówku {@code X-Edge-Token}
     */
    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    /**
     * Zwraca wartość nagłówka Cache-Control dla publicznych redirectów.
     *
     * @return wartość nagłówka {@code Cache-Control}
     */
    public String getCacheControlForRedirects() {
        return cacheControlForRedirects;
    }

    /**
     * Ustawia wartość nagłówka Cache-Control dla publicznych redirectów.
     *
     * <p>
     * Przykładowe wartości:
     * </p>
     *
     * <pre>
     * no-store
     * public, max-age=60
     * public, max-age=60, stale-while-revalidate=300
     * </pre>
     *
     * @param cacheControlForRedirects wartość nagłówka Cache-Control
     */
    public void setCacheControlForRedirects(String cacheControlForRedirects) {
        this.cacheControlForRedirects = cacheControlForRedirects;
    }
}