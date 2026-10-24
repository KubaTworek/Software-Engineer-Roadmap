package pl.jakubtworek.lld.filesystem.domain;

/**
 * Wspólna granica odczytu wpisu: katalog zawiera inne wpisy, plik tekst.
 * Zamknięta hierarchia uniemożliwia wstrzyknięcie mutowalnego podtypu do snapshotu.
 * Nie wymuszamy read na katalogu ani children na pliku: byłyby to fałszywe kontrakty.
 */
public sealed interface FsNode permits DirectoryNode, TextFile {
}
