package com.example.ecommerce.cdn;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Serwis odpowiedzialny za budowanie publicznych URL-i do assetów.
 *
 * W kontekście e-commerce assetami są głównie:
 * - zdjęcia produktów,
 * - miniatury,
 * - bannery,
 * - grafiki kategorii,
 * - pliki publiczne.
 *
 * Ten serwis ukrywa przed resztą aplikacji szczegóły tego,
 * czy pliki mają być serwowane bezpośrednio z origin storage,
 * czy przez CDN.
 */
@Service
public class CdnUrlService {

    /**
     * Konfiguracja CDN/origin.
     *
     * Przykładowe pola:
     * - enabled — czy używać CDN,
     * - baseUrl — publiczny adres CDN,
     * - originBaseUrl — adres źródłowego storage,
     * - version — opcjonalna wersja assetów, np. v1.
     */
    private final CdnProperties properties;

    /**
     * Constructor injection.
     *
     * Serwis dostaje konfigurację z propertiesów aplikacji,
     * np. z application.yml.
     */
    public CdnUrlService(CdnProperties properties) {
        this.properties = properties;
    }

    /**
     * Buduje publiczny URL do assetu.
     *
     * Obsługuje trzy przypadki:
     *
     * 1. Pusty path:
     *    zwracamy wartość bez zmian.
     *
     * 2. Pełny URL:
     *    jeśli path zaczyna się od http:// albo https://,
     *    próbujemy przepisać URL origin na URL CDN.
     *
     * 3. Ścieżka względna:
     *    dokładamy prefix CDN albo origin oraz opcjonalną wersję.
     *
     * Przykład:
     *
     * path:
     * /products/headphones.jpg
     *
     * CDN enabled:
     * https://cdn.example.com/v1/products/headphones.jpg
     *
     * CDN disabled:
     * https://assets.example.com/v1/products/headphones.jpg
     */
    public String publicAssetUrl(String path) {
        /*
         * Brak tekstu oznacza null, pusty string albo same białe znaki.
         *
         * Nie próbujemy wtedy budować URL-a, bo nie ma poprawnej ścieżki assetu.
         */
        if (!StringUtils.hasText(path)) {
            return path;
        }

        /*
         * Jeśli dostaliśmy już pełny URL, nie traktujemy go jak ścieżki względnej.
         *
         * Przykład:
         * https://assets.example.com/products/a.jpg
         *
         * Jeśli CDN jest włączony, rewriteOriginUrl może przepisać origin na CDN.
         */
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return rewriteOriginUrl(path);
        }

        /*
         * Normalizujemy ścieżkę tak, żeby zawsze zaczynała się od "/".
         *
         * Dzięki temu unikamy błędów typu:
         * https://cdn.example.comv1products/a.jpg
         * albo
         * https://cdn.example.com/v1products/a.jpg
         */
        String normalized = path.startsWith("/") ? path : "/" + path;

        /*
         * Wybór prefixu zależy od konfiguracji.
         *
         * Jeśli CDN jest włączony, używamy publicznego adresu CDN.
         * Jeśli CDN jest wyłączony, używamy originBaseUrl, czyli źródłowego storage.
         */
        String prefix = properties.enabled()
                ? properties.baseUrl()
                : properties.originBaseUrl();

        /*
         * Opcjonalna wersja assetów.
         *
         * To pomaga przy cache bustingu.
         * Zmiana version z v1 na v2 powoduje, że frontend zaczyna używać nowych URL-i,
         * a stare assety mogą nadal być cache’owane bez konfliktu.
         */
        if (StringUtils.hasText(properties.version())) {
            return trimTrailingSlash(prefix) + "/" + properties.version() + normalized;
        }

        return trimTrailingSlash(prefix) + normalized;
    }

    /**
     * Przepisuje pełny origin URL na CDN URL.
     *
     * Przykład:
     *
     * origin:
     * https://assets.example.com/products/a.jpg
     *
     * CDN:
     * https://cdn.example.com/products/a.jpg
     *
     * Jeśli CDN jest wyłączony albo originBaseUrl nie jest skonfigurowany,
     * URL zostaje zwrócony bez zmian.
     */
    private String rewriteOriginUrl(String url) {
        /*
         * Nie przepisujemy URL-i, jeśli CDN jest wyłączony.
         *
         * Nie przepisujemy też, jeśli nie znamy originBaseUrl,
         * bo nie wiemy, jaki fragment adresu zastąpić.
         */
        if (!properties.enabled() || !StringUtils.hasText(properties.originBaseUrl())) {
            return url;
        }

        /*
         * Usuwamy końcowe slashe z base URL-i, żeby replace działał przewidywalnie.
         *
         * Ten replace zakłada, że pełny URL zaczyna się od originBaseUrl.
         * Jeśli URL pochodzi z innej domeny, pozostanie bez zmian.
         */
        return url.replace(
                trimTrailingSlash(properties.originBaseUrl()),
                trimTrailingSlash(properties.baseUrl())
        );
    }

    /**
     * Usuwa końcowy slash z URL-a bazowego.
     *
     * Dzięki temu składanie:
     *
     * baseUrl + "/" + version + path
     *
     * nie generuje podwójnych slashy, np.:
     * https://cdn.example.com//v1/products/a.jpg
     */
    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }

        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}