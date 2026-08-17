package com.example.ratelimiter.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DynamicConfigService jest centralnym miejscem dostępu do aktualnych reguł Rate Limitera.
 *
 * Jego rola:
 * - trzyma konfigurację reguł z RateLimiterProperties,
 * - wystawia listę aktywnych reguł,
 * - sortuje reguły po priorytecie,
 * - cache'uje wynik, żeby nie przeliczać listy przy każdym requestcie,
 * - pozwala dynamicznie dodać/zaktualizować regułę,
 * - pozwala wyłączyć regułę,
 * - wersjonuje konfigurację.
 *
 * Z tej klasy korzysta m.in. RuleMatcher.
 *
 * Przepływ:
 *
 * AdminController
 *   -> DynamicConfigService.upsertRule(...) / disableRule(...)
 *   -> invalidate cache + increment version
 *
 * RateLimitFilter
 *   -> RateLimiterEngine
 *   -> RuleMatcher
 *   -> DynamicConfigService.allRules()
 */
@Service
public class DynamicConfigService {

    /**
     * Główna konfiguracja Rate Limitera.
     *
     * W tym projekcie reguły są trzymane w properties.getRules().
     * Na starcie aplikacji pochodzą z application.yml,
     * ale Admin API może je później modyfikować w pamięci procesu.
     *
     * Ważne:
     * ta implementacja nie zapisuje zmian do trwałej bazy danych.
     * Po restarcie aplikacji dynamiczne zmiany z Admin API znikną.
     */
    private final RateLimiterProperties properties;

    /**
     * Lokalny cache listy aktywnych reguł.
     *
     * RuleMatcher może być wywoływany dla każdego requestu,
     * więc nie chcemy za każdym razem:
     * - filtrować reguł,
     * - sortować ich po priority,
     * - tworzyć nowej listy.
     *
     * Cache przechowuje gotową listę aktywnych reguł pod kluczem "all-rules".
     */
    private final Cache<String, List<RateLimiterProperties.Rule>> cache;

    /**
     * Wersja konfiguracji.
     *
     * Każda zmiana reguł zwiększa version.
     *
     * Przydaje się do:
     * - debug endpointów,
     * - sprawdzania, czy config został odświeżony,
     * - logów,
     * - potencjalnej synchronizacji wielu instancji.
     */
    private final AtomicLong version = new AtomicLong(1);

    public DynamicConfigService(RateLimiterProperties properties) {
        this.properties = properties;

        /*
         * Budujemy cache Caffeine zgodnie z konfiguracją.
         *
         * maximumSize:
         * - maksymalna liczba wpisów w cache'u.
         *
         * expireAfterWrite:
         * - po jakim czasie wpis ma wygasnąć od momentu zapisu.
         *
         * W praktyce ten cache ma tylko jeden główny wpis: "all-rules".
         * Mimo tego Caffeine daje wygodny mechanizm TTL i invalidacji.
         */
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getConfigCache().getMaximumSize())
                .expireAfterWrite(Duration.ofSeconds(properties.getConfigCache().getExpireAfterWriteSeconds()))
                .build();
    }

    /**
     * Zwraca aktualną listę aktywnych reguł.
     *
     * To jest metoda używana przez RuleMatcher.
     *
     * Reguły są:
     * - pobierane z properties,
     * - filtrowane po enabled=true,
     * - sortowane po priority rosnąco,
     * - zapisywane w cache'u.
     *
     * Niższy priority oznacza wcześniejsze dopasowanie/przetwarzanie.
     *
     * Dzięki cache'owi większość requestów nie musi ponownie sortować reguł.
     */
    public List<RateLimiterProperties.Rule> allRules() {
        return cache.get("all-rules", key -> properties.getRules().stream()
                .filter(RateLimiterProperties.Rule::isEnabled)
                .sorted(Comparator.comparingInt(RateLimiterProperties.Rule::getPriority))
                .toList());
    }

    /**
     * Zwraca aktualną wersję konfiguracji.
     *
     * Admin/debug API może pokazywać version,
     * żeby łatwo sprawdzić, czy zmiany reguł zostały zastosowane.
     */
    public long version() {
        return version.get();
    }

    /**
     * Dodaje nową regułę albo aktualizuje istniejącą po tym samym ID.
     *
     * Mechanizm:
     * - szukamy reguły o tym samym id,
     * - jeśli istnieje, usuwamy starą,
     * - dodajemy nową,
     * - invalidujemy cache,
     * - zwiększamy wersję konfiguracji.
     *
     * To pozwala Admin API zmieniać limity w runtime.
     *
     * Przykład:
     * można podnieść limit dla tenanta enterprise
     * bez restartowania aplikacji.
     */
    public void upsertRule(RateLimiterProperties.Rule rule) {
        Optional<RateLimiterProperties.Rule> existing = properties.getRules().stream()
                .filter(r -> r.getId().equals(rule.getId()))
                .findFirst();

        existing.ifPresent(properties.getRules()::remove);

        properties.getRules().add(rule);

        invalidate();
    }

    /**
     * Wyłącza regułę o podanym ID.
     *
     * Reguła nie jest fizycznie usuwana.
     * Zmieniamy tylko enabled=false.
     *
     * Dzięki temu:
     * - łatwiej debugować konfigurację,
     * - można potencjalnie przywrócić regułę,
     * - Admin API może pokazać, że reguła istnieje, ale jest nieaktywna.
     *
     * allRules() zwraca tylko enabled=true,
     * więc wyłączona reguła przestaje być używana przez RuleMatcher.
     */
    public boolean disableRule(String id) {
        boolean changed = false;

        for (RateLimiterProperties.Rule rule : properties.getRules()) {
            if (rule.getId().equals(id)) {
                rule.setEnabled(false);
                changed = true;
            }
        }

        if (changed) {
            invalidate();
        }

        return changed;
    }

    /**
     * Czyści cache i zwiększa wersję konfiguracji.
     *
     * Ta metoda musi być wywołana po każdej zmianie reguł.
     *
     * Bez invalidacji RuleMatcher mógłby nadal używać starej,
     * zcache'owanej listy reguł.
     */
    public void invalidate() {
        cache.invalidateAll();
        version.incrementAndGet();
    }
}