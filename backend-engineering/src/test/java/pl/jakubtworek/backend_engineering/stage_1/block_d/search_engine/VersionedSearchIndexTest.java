package pl.jakubtworek.backend_engineering.stage_1.block_d.search_engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VersionedSearchIndexTest {

    @Test
    void staleDeliveryCannotOverwriteNewerProjection() {
        VersionedSearchIndex index = new VersionedSearchIndex();

        assertThat(index.apply(SearchDocument.active("p-1", 2, "Java concurrency", "memory model"))).isTrue();
        assertThat(index.apply(SearchDocument.active("p-1", 1, "Old title", "stale"))).isFalse();

        assertThat(index.search("java", null, 10))
                .extracting(SearchHit::title)
                .containsExactly("Java concurrency");
        assertThat(index.latestVersion("p-1")).isEqualTo(2);
    }

    @Test
    void tombstonePreventsLateEventFromResurrectingDocument() {
        VersionedSearchIndex index = new VersionedSearchIndex();
        index.apply(SearchDocument.active("p-1", 1, "Java", "backend"));
        index.apply(SearchDocument.tombstone("p-1", 3));

        assertThat(index.apply(SearchDocument.active("p-1", 2, "Java", "late update"))).isFalse();
        assertThat(index.search("java", null, 10)).isEmpty();
    }

    @Test
    void searchAfterUsesStableScoreAndIdCursor() {
        VersionedSearchIndex index = new VersionedSearchIndex();
        index.apply(SearchDocument.active("a", 1, "Java backend", "concurrency"));
        index.apply(SearchDocument.active("b", 1, "Java", "backend"));
        index.apply(SearchDocument.active("c", 1, "Java", "testing"));

        List<SearchHit> firstPage = index.search("java backend", null, 2);
        List<SearchHit> secondPage = index.search("java backend", SearchCursor.after(firstPage.getLast()), 2);

        assertThat(firstPage).extracting(SearchHit::id).containsExactly("a", "b");
        assertThat(secondPage).extracting(SearchHit::id).containsExactly("c");
    }

    @Test
    void naiveProjectionLetsAnOldEventWin() {
        NaiveSearchProjection projection = new NaiveSearchProjection();
        projection.apply(SearchDocument.active("p-1", 2, "Current", ""));
        projection.apply(SearchDocument.active("p-1", 1, "Stale", ""));

        assertThat(projection.get("p-1").title()).isEqualTo("Stale");
    }

    @Test
    void pointInTimeKeepsPaginationStableWhileLiveIndexChanges() {
        VersionedSearchIndex live = new VersionedSearchIndex();
        live.apply(SearchDocument.active("a", 1, "Java", "backend"));
        live.apply(SearchDocument.active("b", 1, "Java", "backend"));
        SearchPointInTime pointInTime = live.openPointInTime();
        List<SearchHit> firstPage = pointInTime.search("java", null, 1);

        live.apply(SearchDocument.tombstone("b", 2));
        live.apply(SearchDocument.active("c", 1, "Java", "new"));

        assertThat(pointInTime.search("java", SearchCursor.after(firstPage.getFirst()), 10))
                .extracting(SearchHit::id)
                .containsExactly("b");
        assertThat(live.search("java", null, 10))
                .extracting(SearchHit::id)
                .containsExactly("a", "c");
    }
}
