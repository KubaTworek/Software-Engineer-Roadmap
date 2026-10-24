package pl.jakubtworek.lld.library;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Stały katalog pozwala opisywać scenariusze, bez losowego czasu i globalnego stanu.
 */
public final class Fixtures {

    public static final BookId B1 = new BookId("B1");
    public static final BookId B2 = new BookId("B2");
    public static final BookId B3 = new BookId("B3");
    public static final MemberId M1 = new MemberId("M1");
    public static final MemberId M2 = new MemberId("M2");
    public static final MemberId M3 = new MemberId("M3");
    public static final MemberId M4 = new MemberId("M4");
    public static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private Fixtures() {
    }

    public static Library library(int firstCopies) {
        return library(firstCopies, CLOCK);
    }

    public static Library library(int firstCopies, Clock clock) {
        var books = new ArrayList<Book>();
        var copies = new ArrayList<BookCopy>();
        for (int i = 1; i <= 6; i++) {
            books.add(new Book(new BookId("B" + i), "Tytuł " + i, "Autor"));
        }
        for (int i = 1; i <= firstCopies; i++) {
            copies.add(new BookCopy(new CopyId("C" + i), B1));
        }
        for (int i = 2; i <= 6; i++) {
            copies.add(new BookCopy(new CopyId("C" + (i + 1)), new BookId("B" + i)));
        }
        return new Library(books, copies, List.of(new Member(M1, "Ala", Membership.STANDARD), new Member(M2, "Bartek",
                Membership.STANDARD), new Member(M3, "Celina", Membership.EXTENDED), new Member(M4, "Darek",
                Membership.STANDARD)), clock);
    }
}
