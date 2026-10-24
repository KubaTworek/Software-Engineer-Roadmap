package pl.jakubtworek.lld.cache.domain;

/**
 * Odczyt fizycznego stanu BEZ sprzątania i bez źródeł czasu. Rozmiar może zawierać
 * wygasłe wpisy. Nie mylić z size(), które usuwa wygasłe wpisy.
 * scheduledRecords == timedEntries: indeksowany kopiec nie przechowuje starych TTL.
 * Pole compactions zachowuje zgodność raportu; wynosi zero, bo nie ma kompaktowania.
 */
public record CacheDiagnostics(int capacity, EvictionPolicy policy, int physicalSize, int timedEntries,
        int scheduledRecords, long compactions) {
}
