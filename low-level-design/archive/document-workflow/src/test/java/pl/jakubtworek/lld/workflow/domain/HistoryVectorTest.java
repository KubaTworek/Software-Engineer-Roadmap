package pl.jakubtworek.lld.workflow.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Granice potęg dwójki ujawniają błędy powiększania korzenia wektora.
 */
class HistoryVectorTest {

    @Test
    void appendMatchesListAndPreservesHistoricalVersions() {
        List<WorkflowEvent> vector = HistoryVector.events(List.of());
        var model = new ArrayList<WorkflowEvent>();
        var old = new ArrayList<List<WorkflowEvent>>();
        var expected = new ArrayList<List<WorkflowEvent>>();
        for (int i = 1; i <= 4097; i++) {
            if ((i & (i - 1)) == 0) {
                old.add(vector);
                expected.add(List.copyOf(model));
            }
            var event = new WorkflowEvent(i, Instant.EPOCH, new UserId("author"), WorkflowEvent.Type.CREATED, 1, 0,
                    Optional.empty(), Optional.empty(), DocumentStatus.DRAFT);
            vector = HistoryVector.append(vector, event);
            model.add(event);
            assertEquals(event, vector.get(i - 1));
            if ((i & (i - 1)) == 0) {
                assertEquals(model, vector);
            }
        }
        assertEquals(model, vector);
        for (int i = 0; i < old.size(); i++) {
            assertEquals(expected.get(i), old.get(i));
        }
        assertThrows(UnsupportedOperationException.class, () -> old.getLast().clear());
    }
}
