package com.example.ecommerce.cdn;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller odpowiedzialny za zwracanie publicznych URL-i do assetów przez CDN.
 *
 * W e-commerce assetami są najczęściej:
 * - zdjęcia produktów,
 * - miniatury,
 * - bannery marketingowe,
 * - pliki statyczne,
 * - dokumenty publiczne.
 *
 * Controller nie generuje URL-a samodzielnie.
 * Deleguje tę logikę do CdnUrlService, który wie, czy CDN jest włączony
 * i jak zbudować finalny publiczny adres.
 */
@RestController
public class CdnController {

    /**
     * Serwis odpowiedzialny za budowanie publicznych URL-i assetów.
     *
     * Przykładowo:
     * /products/headphones.jpg
     *
     * może zostać zamienione na:
     * https://cdn.example.com/v1/products/headphones.jpg
     */
    private final CdnUrlService cdn;

    /**
     * Constructor injection.
     *
     * Controller dostaje gotowy CdnUrlService z kontenera Springa.
     */
    public CdnController(CdnUrlService cdn) {
        this.cdn = cdn;
    }

    /**
     * Zwraca publiczny URL do assetu.
     *
     * Endpoint:
     * GET /api/assets/cdn-url?path=/products/image.jpg
     *
     * Parametr path:
     * - może być ścieżką względną, np. /products/image.jpg,
     * - może być też pełnym URL-em origin, jeśli serwis CDN ma go przepisać.
     *
     * Ten endpoint jest przydatny, gdy frontend albo admin panel przechowuje
     * tylko techniczną ścieżkę pliku, a potrzebuje pełnego publicznego adresu.
     *
     * Ważne:
     * Controller nie sprawdza, czy plik fizycznie istnieje.
     * Odpowiada tylko za wygenerowanie adresu publicznego.
     */
    @GetMapping("/api/assets/cdn-url")
    public AssetUrlResponse cdnUrl(@RequestParam String path) {
        return new AssetUrlResponse(cdn.publicAssetUrl(path));
    }

    /**
     * Prosty DTO odpowiedzi.
     *
     * Record dobrze pasuje do krótkich, niemutowalnych odpowiedzi API.
     *
     * Przykład odpowiedzi:
     *
     * {
     *   "url": "https://cdn.example.com/v1/products/image.jpg"
     * }
     */
    public record AssetUrlResponse(String url) {
    }
}