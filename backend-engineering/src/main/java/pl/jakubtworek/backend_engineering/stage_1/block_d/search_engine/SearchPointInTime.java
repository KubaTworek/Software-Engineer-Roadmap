package pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine;

import java.util.List;

/** Immutable logical search snapshot used with search_after across concurrent updates. */
public final class SearchPointInTime {

    private final VersionedSearchIndex snapshot;

    SearchPointInTime(VersionedSearchIndex snapshot) {
        this.snapshot = snapshot;
    }

    public List<SearchHit> search(String query, SearchCursor after, int limit) {
        return snapshot.search(query, after, limit);
    }
}
