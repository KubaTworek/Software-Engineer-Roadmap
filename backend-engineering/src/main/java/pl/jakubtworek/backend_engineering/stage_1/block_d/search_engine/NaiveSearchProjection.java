package pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine;

import java.util.HashMap;
import java.util.Map;

/** Deliberate counterexample: delivery order, not source version, wins. */
public final class NaiveSearchProjection {

    private final Map<String, SearchDocument> documents = new HashMap<>();

    public void apply(SearchDocument document) {
        if (document.deleted()) {
            documents.remove(document.id());
        } else {
            documents.put(document.id(), document);
        }
    }

    public SearchDocument get(String id) {
        return documents.get(id);
    }
}
