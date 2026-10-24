package pl.jakubtworek.lld.library;

import java.util.Objects;

/**
 * Opis katalogowy tytułu, a nie zasób do wypożyczenia. Nie zawiera available:
 * dwie kopie tej samej książki mogą być jednocześnie wolna i wypożyczona.
 * Kod identyfikuje wpis katalogu; tytuł i autor mogą się powtarzać.
 */
public record Book(BookId id, String title, String author) {

    public Book {
        Objects.requireNonNull(id, "id");
        title = Text.nonBlank(title);
        author = Text.nonBlank(author);
    }
}
