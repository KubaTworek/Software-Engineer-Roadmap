package pl.jakubtworek.lld.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.library.Fixtures.*;

/**
 * Walidacja chroni znaczenie wartości, a konfiguracja relacje między obiektami.
 */
class ValueObjectsTest {

    @Test
    void normalizationMakesDuplicateCodesVisibleButNamesNeedNotBeUnique() {
        assertEquals(new BookId("B1"), new BookId(" b1 "));
        assertEquals(new CopyId("C-1"), new CopyId(" c-1 "));
        assertEquals(new MemberId("M1"), new MemberId(" m1 "));
        var book = new Book(B1, " Tytuł ", " Autor ");
        assertEquals("Tytuł", book.title());
        assertThrows(IllegalArgumentException.class, () -> new Library(List.of(book, new Book(new BookId("b1"), "Inny",
                "A")), List.of(), List.of(), CLOCK));
        var library = new Library(List.of(book, new Book(B2, "Tytuł", "Autor")), List.of(), List.of(new Member(M1,
                "Ala", Membership.STANDARD), new Member(M2, "Ala", Membership.EXTENDED)), CLOCK);
        assertEquals(2, library.snapshot().books().size());
        assertEquals(2, library.snapshot().members().size());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "A B", "A/B", "ą", "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567" })
    void invalidIdentifiersAreRejectedForEveryKind(String value) {
        assertThrows(IllegalArgumentException.class, () -> new BookId(value));
        assertThrows(IllegalArgumentException.class, () -> new CopyId(value));
        assertThrows(IllegalArgumentException.class, () -> new MemberId(value));
    }

    @Test
    void nullAndBlankValuesCannotFormDomainObjects() {
        assertThrows(NullPointerException.class, () -> new BookId(null));
        assertThrows(NullPointerException.class, () -> new CopyId(null));
        assertThrows(NullPointerException.class, () -> new MemberId(null));
        assertThrows(NullPointerException.class, () -> new Book(null, "T", "A"));
        assertThrows(IllegalArgumentException.class, () -> new Book(B1, " ", "A"));
        assertThrows(IllegalArgumentException.class, () -> new Book(B1, "T", " "));
        assertThrows(NullPointerException.class, () -> new BookCopy(new CopyId("C1"), null));
        assertThrows(NullPointerException.class, () -> new BookCopy(null, B1));
        assertThrows(NullPointerException.class, () -> new Member(null, "A", Membership.STANDARD));
        assertThrows(NullPointerException.class, () -> new Member(M1, "A", null));
        assertThrows(IllegalArgumentException.class, () -> new Member(M1, " ", Membership.STANDARD));
        var id = new LoanId(new java.util.UUID(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Loan(id, new CopyId("C1"), M1, NOW, NOW, false));
        assertThrows(IllegalArgumentException.class, () -> new Loan(id, new CopyId("C1"), M1, NOW, NOW.minusSeconds(1),
                false));
        assertThrows(NullPointerException.class, () -> new LoanId(null));
        assertThrows(IllegalArgumentException.class, () -> LoanId.parse("nie-uuid"));
        assertEquals(id, LoanId.parse(" " + id.value() + " "));
    }

    @Test
    void catalogRejectsDuplicateEntitiesAndDanglingCopyReference() {
        var book = new Book(B1, "T", "A");
        var copy = new BookCopy(new CopyId("C1"), B1);
        var member = new Member(M1, "A", Membership.STANDARD);
        assertThrows(IllegalArgumentException.class, () -> new Library(List.of(book), List.of(copy, copy),
                List.of(member), CLOCK));
        assertThrows(IllegalArgumentException.class, () -> new Library(List.of(book), List.of(copy), List.of(member,
                member), CLOCK));
        assertThrows(IllegalArgumentException.class, () -> new Library(List.of(), List.of(copy), List.of(member),
                CLOCK));
        assertThrows(NullPointerException.class, () -> new Library(List.of(book), List.of(copy), List.of(member),
                null));
        assertThrows(NullPointerException.class, () -> new Library(null, List.of(), List.of(), CLOCK));
    }
}
