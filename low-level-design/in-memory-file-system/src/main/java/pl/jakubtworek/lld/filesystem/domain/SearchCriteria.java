package pl.jakubtworek.lld.filesystem.domain;

import java.util.Objects;

/**
 * Czysty warunek pojedynczego wpisu. AND/OR komponują zachowanie zamiast
 * rozbudowywać find o flagi. Warunek nie steruje zejściem do dzieci: katalog
 * niespełniający warunku nadal może zawierać pasujący plik.
 * Własne implementacje muszą być deterministyczne i bez efektów ubocznych.
 */
@FunctionalInterface
public interface SearchCriteria {

    enum Kind {

        FILE, DIRECTORY
    }

    boolean matches(FsPath path, FsNode node);

    default SearchCriteria and(SearchCriteria other) {
        Objects.requireNonNull(other);
        return (path, node) -> matches(path, node) && other.matches(path, node);
    }

    default SearchCriteria or(SearchCriteria other) {
        Objects.requireNonNull(other);
        return (path, node) -> matches(path, node) || other.matches(path, node);
    }

    static SearchCriteria all() {
        return (path, node) -> true;
    }

    static SearchCriteria nameContains(String fragment) {
        Objects.requireNonNull(fragment);
        if (fragment.isEmpty())
            throw new IllegalArgumentException("Pusty fragment nazwy");
        return (path, node) -> path.name().contains(fragment);
    }

    static SearchCriteria kind(Kind kind) {
        Objects.requireNonNull(kind);
        return (path, node) -> kind == Kind.FILE ? node instanceof TextFile : node instanceof DirectoryNode;
    }

    /**
     * Rozszerzenie bez kropki, np. txt. To końcówka nazwy pliku, z rozróżnieniem liter.
     */
    static SearchCriteria extension(String extension) {
        Objects.requireNonNull(extension);
        FsPath.validateName(extension);
        if (extension.contains("."))
            throw new IllegalArgumentException("Rozszerzenie bez kropek");
        return (path, node) -> node instanceof TextFile && path.name().endsWith("." + extension);
    }
}
