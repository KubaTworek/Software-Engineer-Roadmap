package pl.jakubtworek.lld.workflow.domain;

import java.util.Objects;

/**
 * Referencja do dokładnie jednego etapu jednej rundy i rewizji dokumentu.
 * To ochrona przed spóźnionym poleceniem, nie blokada wątkowa ani sekret.
 */
public record ReviewToken(DocumentId documentId, int round, long revision, String stageId) {

    public ReviewToken {
        Objects.requireNonNull(documentId);
        if (round < 1 || revision < 1)
            throw new IllegalArgumentException("Niepoprawna runda lub rewizja tokenu");
        if (stageId == null || stageId.isBlank())
            throw new IllegalArgumentException("Brak etapu tokenu");
    }
}
