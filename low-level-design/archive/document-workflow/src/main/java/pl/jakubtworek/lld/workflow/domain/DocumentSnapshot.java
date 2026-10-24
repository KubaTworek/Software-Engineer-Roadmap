package pl.jakubtworek.lld.workflow.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Wersja liczy przyjęte zmiany agregatu, rewizja zmiany treści, lastRound wszystkie
 * złożenia. DRAFT po wycofaniu nie ma bieżącej rundy, lecz pamięta lastRound.
 * Archiwum i historia należą do tej samej publikowanej wartości, bez uchwytów
 * do mutacji. Nie jest to zapis odtwarzany przez event sourcing.
 */
public record DocumentSnapshot(DocumentId id, UserId author, DocumentContent content, long revision,
        DocumentStatus status, Optional<ReviewRound> review, List<WorkflowEvent> history, long version, int lastRound,
        List<ContentRevision> revisions) {

    public DocumentSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(author);
        Objects.requireNonNull(content);
        Objects.requireNonNull(status);
        Objects.requireNonNull(review);
        history = HistoryVector.events(history);
        revisions = HistoryVector.revisions(revisions, author);
        if (revision < 1 || version < 1 || lastRound < 0 || history.isEmpty() || revisions.isEmpty()) {
            throw new IllegalArgumentException("Niepoprawne liczniki lub brak historii");
        }
        if ((status == DocumentStatus.DRAFT) != review.isEmpty())
            throw new IllegalArgumentException("Niespójny stan rundy");
        if (revisions.getLast().number() != revision || !revisions.getLast().content().equals(content)) {
            throw new IllegalArgumentException("Archiwum nie opisuje bieżącej treści");
        }
        if (review.isPresent()) {
            ReviewRound round = review.get();
            if (round.number() != lastRound || revision < round.submittedRevision())
                throw new IllegalArgumentException("Niespójna runda");
            ContentRevision archived = revisions.get(Math.toIntExact(round.submittedRevision() - 1));
            if (!archived.content().equals(round.submittedContent()))
                throw new IllegalArgumentException("Brak złożonej treści w archiwum");
            if (status != DocumentStatus.REJECTED && revision != round.submittedRevision())
                throw new IllegalArgumentException("Złożona rewizja zmieniła się");
            if (status == DocumentStatus.APPROVED && round.approvals().isEmpty())
                throw new IllegalArgumentException("Brak akceptacji");
        }
        WorkflowEvent last = history.getLast();
        if (last.revision() != revision || last.resultingStatus() != status || last.round() != lastRound
                || last.sequence() != version) {
            throw new IllegalArgumentException("Historia nie opisuje bieżącego snapshotu");
        }
    }
}
