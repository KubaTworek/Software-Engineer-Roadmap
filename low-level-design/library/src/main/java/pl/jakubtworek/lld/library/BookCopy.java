package pl.jakubtworek.lld.library;

import java.util.Objects;

/**
 * Egzemplarz odsyła do opisu tytułu. Nie powielamy flagi wypożyczenia:
 * jedynym źródłem zajętości jest mapa aktywnych wypożyczeń w snapshotcie.
 * Library sprawdza, czy wskazany tytuł rzeczywiście istnieje w jej katalogu.
 */
public record BookCopy(CopyId id, BookId bookId) {

    public BookCopy {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bookId, "bookId");
    }
}
