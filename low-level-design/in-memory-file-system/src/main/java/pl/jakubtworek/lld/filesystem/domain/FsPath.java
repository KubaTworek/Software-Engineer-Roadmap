package pl.jakubtworek.lld.filesystem.domain;

import java.util.List;
import java.util.Objects;

/**
 * Kanoniczna ścieżka domenowa, niezależna od systemu operacyjnego hosta.
 * Nie używamy java.nio.file.Path: C:\ i backslash nie mają tu znaczenia Windows.
 * Odrzucamy aliasy /a/../b zamiast po cichu zmieniać intencję użytkownika.
 * Pusta lista segmentów oznacza jedyny korzeń, a nie pustą ścieżkę tekstową.
 */
public record FsPath(List<String> segments) implements Comparable<FsPath> {

    public FsPath {
        segments = List.copyOf(segments);
        segments.forEach(FsPath::validateName);
    }

    public static FsPath parse(String value) {
        Objects.requireNonNull(value);
        if (!value.startsWith("/"))
            throw new IllegalArgumentException("Wymagana ścieżka bezwzględna");
        if (value.equals("/"))
            return new FsPath(List.of());
        return new FsPath(List.of(value.substring(1).split("/", -1)));
    }

    static void validateName(String name) {
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0 || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Niepoprawna nazwa wpisu: " + name);
        }
    }

    public boolean isRoot() {
        return segments.isEmpty();
    }

    public String name() {
        return isRoot() ? "/" : segments.getLast();
    }

    public FsPath parent() {
        if (isRoot())
            throw new IllegalArgumentException("Korzeń nie ma rodzica");
        return new FsPath(segments.subList(0, segments.size() - 1));
    }

    public FsPath child(String name) {
        validateName(name);
        var copy = new java.util.ArrayList<>(segments);
        copy.add(name);
        return new FsPath(copy);
    }

    /**
     * Prefiks segmentów, nie tekstu: /a obejmuje /a/b, ale nie /ab.
     */
    public boolean contains(FsPath other) {
        return other.segments.size() >= segments.size() && other.segments.subList(0, segments.size()).equals(segments);
    }

    @Override
    public String toString() {
        return "/" + String.join("/", segments);
    }

    @Override
    public int compareTo(FsPath other) {
        return toString().compareTo(other.toString());
    }
}
