package pl.jakubtworek.lld.filesystem.domain;

import java.util.Objects;

/**
 * Pełny tekst pliku jako niemutowalna wartość. Pusty tekst jest poprawnym plikiem.
 * Nie interpretujemy bajtów, kodowania ani separatorów linii; zapis zastępuje
 * całą treść. String można bezpiecznie współdzielić między dawnymi widokami.
 */
public record TextFile(String content) implements FsNode {

    public TextFile {
        Objects.requireNonNull(content);
    }
}
