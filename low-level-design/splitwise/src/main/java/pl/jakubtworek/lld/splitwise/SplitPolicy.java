package pl.jakubtworek.lld.splitwise;

import java.util.Map;

/**
 * Strategy ma trzy rzeczywiste warianty: podział równy, procentowy i kwotowy.
 * Wynik zawiera niepuste, nieujemne udziały sumujące się dokładnie do total.
 * Zero total jest dozwolone dla samodzielnego algorytmu; grupa nie zapisuje
 * zerowego wydatku. Polityka ma być deterministyczna i bez efektów ubocznych.
 * Grupa nadal sprawdza znanych uczestników i poprawność zwróconego podziału.
 */
public interface SplitPolicy {

    Map<MemberId, Money> split(Money total);
}
