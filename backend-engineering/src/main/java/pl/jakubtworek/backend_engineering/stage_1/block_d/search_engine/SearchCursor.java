package pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine;

public record SearchCursor(int score, String id) {

    public static SearchCursor after(SearchHit hit) {
        return new SearchCursor(hit.score(), hit.id());
    }
}
