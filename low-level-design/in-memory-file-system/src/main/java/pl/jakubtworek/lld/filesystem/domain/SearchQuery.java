package pl.jakubtworek.lld.filesystem.domain;

import java.util.Objects;

/**
 * Głębokość jest względna wobec startu: 0 bada tylko start, 1 także dzieci.
 * Limit steruje przejściem, a kryterium kwalifikacją; nie są zamiennymi regułami.
 */
public record SearchQuery(SearchCriteria criteria, int maxDepth) {

    public SearchQuery {
        Objects.requireNonNull(criteria);
        if (maxDepth < 0)
            throw new IllegalArgumentException("Ujemna głębokość");
    }

    public static SearchQuery unlimited(SearchCriteria criteria) {
        return new SearchQuery(criteria, Integer.MAX_VALUE);
    }
}
