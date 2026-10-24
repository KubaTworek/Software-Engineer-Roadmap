package pl.jakubtworek.lld.filesystem.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.filesystem.domain.InMemoryFileSystemTest.p;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemException.Reason.*;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemException.Operation.*;

/**
 * Nowe kontrakty sprawdzamy obserwowalnie: odczyty starego widoku, niezależność
 * kopii i kontekst odmowy są ważniejsze od liczby alokowanych węzłów.
 */
class FileSystemExtensionsTest {

    @Test
    void historicalViewReadsListsAndSearchesItsOwnTree() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.createFile(p("/a/file.txt"), "old");
        var before = fs.snapshot();
        fs.copy(p("/a"), p("/copy"));
        fs.write(p("/a/file.txt"), "new");
        fs.move(p("/a"), p("/moved"));
        fs.delete(p("/moved"), DeleteMode.RECURSIVE);
        assertEquals("old", before.read(p("/a/file.txt")));
        assertEquals(List.of(p("/a/file.txt")), before.list(p("/a")));
        assertEquals(List.of(p("/a/file.txt")), before.find(p("/"), ".txt"));
        assertThrows(FileSystemException.class, () -> before.stat(p("/copy")));
        assertEquals("old", fs.read(p("/copy/file.txt")));
    }

    @Test
    void copyChangesIndependentlyInBothDirections() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/source"));
        fs.mkdir(p("/source/sub"));
        fs.createFile(p("/source/sub/f"), "initial");
        fs.copy(p("/source"), p("/copy"));
        fs.write(p("/copy/sub/f"), "copy");
        assertEquals("initial", fs.read(p("/source/sub/f")));
        fs.write(p("/source/sub/f"), "source");
        assertEquals("copy", fs.read(p("/copy/sub/f")));
        fs.delete(p("/copy/sub"), DeleteMode.RECURSIVE);
        assertEquals("source", fs.read(p("/source/sub/f")));
        fs.copy(p("/source/sub/f"), p("/file-copy"));
        fs.write(p("/file-copy"), "");
        assertEquals("source", fs.read(p("/source/sub/f")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "self", "descendant", "collision", "missing", "file-parent", "root-source",
            "root-target" })
    void copyRefusalsPreserveSnapshotAndDescribeContext(String variant) {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.mkdir(p("/a/sub"));
        fs.createFile(p("/file"), "x");
        var before = fs.snapshot();
        String target = switch(variant) {
            case "self" ->
                "/a";
            case "descendant" ->
                "/a/sub/copy";
            case "collision" ->
                "/file";
            case "missing" ->
                "/missing/new";
            case "file-parent" ->
                "/file/new";
            case "root-source" ->
                "/new";
            default ->
                "/";
        };
        var ex = assertThrows(FileSystemException.class, () -> fs.copy(p(variant.equals("root-source") ? "/" : "/a"),
                p(target)));
        assertEquals(COPY, ex.operation());
        assertEquals(switch(variant) {
            case "self", "descendant" ->
                INVALID_COPY;
            case "collision" ->
                ALREADY_EXISTS;
            case "missing" ->
                NOT_FOUND;
            case "file-parent" ->
                NOT_DIRECTORY;
            default ->
                ROOT_PROTECTED;
        }, ex.reason());
        assertSame(before, fs.snapshot());
    }

    @Test
    void errorLocatesFirstBlockingPrefixAndFullOperand() {
        var fs = new InMemoryFileSystem();
        fs.createFile(p("/file"), "x");
        var blocked = assertThrows(FileSystemException.class, () -> fs.mkdir(p("/file/a/b")));
        assertEquals(MKDIR, blocked.operation());
        assertEquals(p("/file/a/b"), blocked.path());
        assertEquals(p("/file"), blocked.problemPath());
        var missing = assertThrows(FileSystemException.class, () -> fs.copy(p("/file"), p("/missing/a/b")));
        assertEquals(COPY, missing.operation());
        assertEquals(p("/missing/a/b"), missing.path());
        assertEquals(p("/missing"), missing.problemPath());
        var read = assertThrows(FileSystemException.class, () -> fs.read(p("/")));
        assertEquals(READ, read.operation());
        assertEquals(NOT_FILE, read.reason());
    }

    @Test
    void criteriaComposeAndDepthPrunesTraversalWithoutFilteringParents() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/docs"));
        fs.mkdir(p("/docs/nested"));
        fs.createFile(p("/docs/a.txt"), "");
        fs.createFile(p("/docs/B.TXT"), "");
        fs.createFile(p("/docs/nested/c.txt"), "");
        var txt = SearchCriteria.kind(SearchCriteria.Kind.FILE).and(SearchCriteria.extension("txt"));
        assertEquals(List.of(p("/docs/a.txt")), fs.find(p("/docs"), new SearchQuery(txt, 1)));
        assertEquals(List.of(), fs.find(p("/docs"), new SearchQuery(txt, 0)));
        assertEquals(List.of(p("/docs/a.txt"), p("/docs/nested/c.txt")), fs.find(p("/docs"),
                SearchQuery.unlimited(txt)));
        var either = txt.or(SearchCriteria.kind(SearchCriteria.Kind.DIRECTORY));
        assertEquals(List.of(p("/docs"), p("/docs/a.txt"), p("/docs/nested")), fs.find(p("/docs"),
                new SearchQuery(either, 1)));
        var before = fs.snapshot();
        assertThrows(IllegalArgumentException.class, () -> new SearchQuery(txt, -1));
        assertThrows(IllegalArgumentException.class, () -> SearchCriteria.extension(".txt"));
        assertThrows(IllegalArgumentException.class, () -> SearchCriteria.extension(""));
        assertSame(before, fs.snapshot());
    }

    @Test
    void depthIsRelativeToFileOrDirectoryStartAndResultsRemainImmutable() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.mkdir(p("/a/b"));
        fs.createFile(p("/a/b/f.txt"), "x");
        assertEquals(List.of(p("/a/b")), fs.find(p("/a/b"), new SearchQuery(SearchCriteria.all(), 0)));
        assertEquals(List.of(p("/a/b/f.txt")), fs.find(p("/a/b/f.txt"), new SearchQuery(SearchCriteria.extension("txt"),
                0)));
        assertThrows(UnsupportedOperationException.class, () -> fs.snapshot().find(p("/"),
                SearchQuery.unlimited(SearchCriteria.all())).clear());
        var before = fs.snapshot();
        assertThrows(NullPointerException.class, () -> fs.delete(p("/a"), null));
        assertSame(before, fs.snapshot());
    }
}
