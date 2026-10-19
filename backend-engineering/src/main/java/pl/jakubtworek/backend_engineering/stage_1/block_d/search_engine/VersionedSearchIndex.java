package pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** In-memory model of a rebuildable, version-aware search projection. */
public final class VersionedSearchIndex {

    private static final Comparator<SearchHit> ORDER = Comparator
            .comparingInt(SearchHit::score).reversed()
            .thenComparing(SearchHit::id);

    private final Map<String, SearchDocument> documents = new HashMap<>();
    private final Map<String, Set<String>> postings = new HashMap<>();
    private final Map<String, Long> latestVersions = new HashMap<>();

    public synchronized boolean apply(SearchDocument incoming) {
        long currentVersion = latestVersions.getOrDefault(incoming.id(), 0L);
        if (incoming.version() <= currentVersion) {
            return false;
        }

        SearchDocument previous = documents.remove(incoming.id());
        if (previous != null) {
            removeFromPostings(previous);
        }
        latestVersions.put(incoming.id(), incoming.version());

        if (!incoming.deleted()) {
            documents.put(incoming.id(), incoming);
            tokens(incoming.title() + " " + incoming.body())
                    .forEach(token -> postings.computeIfAbsent(token, ignored -> new HashSet<>()).add(incoming.id()));
        }
        return true;
    }

    public synchronized List<SearchHit> search(String query, SearchCursor after, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        Set<String> candidates = queryTokens.stream()
                .flatMap(token -> postings.getOrDefault(token, Set.of()).stream())
                .collect(Collectors.toSet());

        List<SearchHit> ordered = new ArrayList<>();
        for (String id : candidates) {
            SearchDocument document = documents.get(id);
            Set<String> documentTokens = tokens(document.title() + " " + document.body());
            int score = (int) queryTokens.stream().filter(documentTokens::contains).count();
            ordered.add(new SearchHit(id, score, document.title()));
        }
        ordered.sort(ORDER);

        return ordered.stream()
                .filter(hit -> after == null || isAfter(hit, after))
                .limit(limit)
                .toList();
    }

    public synchronized long latestVersion(String id) {
        return latestVersions.getOrDefault(id, 0L);
    }

    public synchronized SearchPointInTime openPointInTime() {
        VersionedSearchIndex snapshot = new VersionedSearchIndex();
        documents.values().forEach(snapshot::apply);
        return new SearchPointInTime(snapshot);
    }

    private boolean isAfter(SearchHit hit, SearchCursor cursor) {
        SearchHit cursorHit = new SearchHit(cursor.id(), cursor.score(), "");
        return ORDER.compare(hit, cursorHit) > 0;
    }

    private void removeFromPostings(SearchDocument document) {
        tokens(document.title() + " " + document.body()).forEach(token -> {
            Set<String> ids = postings.get(token);
            if (ids != null) {
                ids.remove(document.id());
                if (ids.isEmpty()) {
                    postings.remove(token);
                }
            }
        });
    }

    private Set<String> tokens(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
