package pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine;

public record SearchDocument(String id, long version, String title, String body, boolean deleted) {

    public SearchDocument {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        title = title == null ? "" : title;
        body = body == null ? "" : body;
    }

    public static SearchDocument active(String id, long version, String title, String body) {
        return new SearchDocument(id, version, title, body, false);
    }

    public static SearchDocument tombstone(String id, long version) {
        return new SearchDocument(id, version, "", "", true);
    }
}
