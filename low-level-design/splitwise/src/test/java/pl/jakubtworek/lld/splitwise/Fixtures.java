package pl.jakubtworek.lld.splitwise;

import java.util.List;

/**
 * Stałe nazwy opisują scenariusze; każda metoda group tworzy nowy, niezależny dziennik.
 */
public final class Fixtures {

    public static final MemberId U1 = new MemberId("U1");
    public static final MemberId U2 = new MemberId("U2");
    public static final MemberId U3 = new MemberId("U3");
    public static final List<MemberId> IDS = List.of(U1, U2, U3);

    private Fixtures() {
    }

    public static ExpenseGroup group() {
        return new ExpenseGroup(List.of(new Member(U1, "Ala"), new Member(U2, "Bartek"), new Member(U3, "Celina")));
    }
}
