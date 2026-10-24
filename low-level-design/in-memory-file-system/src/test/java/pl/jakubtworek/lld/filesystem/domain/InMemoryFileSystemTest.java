package pl.jakubtworek.lld.filesystem.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.filesystem.domain.FileSystemException.Reason.*;

/**
 * Testy badają publiczny stan i odmowy. Przechowanie korzenia sprzed polecenia
 * wykrywa częściową zmianę oraz wyciek mutowalnych map do dawnych widoków.
 */
class InMemoryFileSystemTest {

    static FsPath p(String value) {
        return FsPath.parse(value);
    }

    @Test
    void fullScenarioCreatesEditsSearchesMovesAndDeletes() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/docs"));
        fs.mkdir(p("/archive"));
        fs.createFile(p("/docs/lesson.txt"), "pierwsza lekcja");
        fs.write(p("/docs/lesson.txt"), "druga lekcja");
        fs.move(p("/docs"), p("/archive/course"));
        assertEquals("druga lekcja", fs.read(p("/archive/course/lesson.txt")));
        assertEquals(List.of(p("/archive/course/lesson.txt")), fs.find(p("/"), "lesson"));
        denied(fs, NOT_FOUND, () -> fs.stat(p("/docs")));
        fs.delete(p("/archive/course"), DeleteMode.RECURSIVE);
        assertEquals(List.of(), fs.list(p("/archive")));
    }

    @Test
    void siblingNamesAreUniqueAcrossBothTypesAndCaseSensitive() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.createFile(p("/A"), "");
        denied(fs, ALREADY_EXISTS, () -> fs.createFile(p("/a"), "x"));
        denied(fs, ALREADY_EXISTS, () -> fs.mkdir(p("/A")));
        assertEquals(List.of(p("/A"), p("/a")), fs.list(p("/")));
    }

    @Test
    void missingParentsAreNotCreatedAndFileCannotBeTraversed() {
        var fs = new InMemoryFileSystem();
        denied(fs, NOT_FOUND, () -> fs.mkdir(p("/a/b")));
        fs.createFile(p("/file"), "");
        denied(fs, NOT_DIRECTORY, () -> fs.mkdir(p("/file/sub")));
        denied(fs, NOT_DIRECTORY, () -> fs.read(p("/file/sub")));
        denied(fs, NOT_DIRECTORY, () -> fs.list(p("/file")));
        denied(fs, NOT_FILE, () -> fs.read(p("/")));
        denied(fs, NOT_FILE, () -> fs.write(p("/"), "x"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "create", "mkdir", "delete", "source", "target" })
    void rootCannotBeCreatedDeletedOrMoved(String operation) {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        denied(fs, ROOT_PROTECTED, () -> {
            switch(operation) {
                case "create" ->
                    fs.createFile(p("/"), "");
                case "mkdir" ->
                    fs.mkdir(p("/"));
                case "delete" ->
                    fs.delete(p("/"), DeleteMode.RECURSIVE);
                case "source" ->
                    fs.move(p("/"), p("/x"));
                default ->
                    fs.move(p("/a"), p("/"));
            }
        });
    }

    @Test
    void nonRecursiveDeleteRefusesNonEmptyDirectory() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.createFile(p("/a/f"), "");
        denied(fs, DIRECTORY_NOT_EMPTY, () -> fs.delete(p("/a"), DeleteMode.EMPTY_ONLY));
        fs.delete(p("/a/f"), DeleteMode.EMPTY_ONLY);
        fs.delete(p("/a"), DeleteMode.EMPTY_ONLY);
        assertTrue(fs.list(p("/")).isEmpty());
        denied(fs, NOT_FOUND, () -> fs.delete(p("/missing"), DeleteMode.RECURSIVE));
    }

    @Test
    void cycleAndSelfMoveRefusalsPreserveWholeTree() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.mkdir(p("/a/sub"));
        fs.createFile(p("/a/sub/f"), "tekst");
        denied(fs, INVALID_MOVE, () -> fs.move(p("/a"), p("/a/sub/renamed")));
        denied(fs, INVALID_MOVE, () -> fs.move(p("/a"), p("/a")));
        denied(fs, INVALID_MOVE, () -> fs.move(p("/a/sub/f"), p("/a/sub/f")));
        assertEquals("tekst", fs.read(p("/a/sub/f")));
    }

    @Test
    void textualPrefixIsNotAncestry() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.mkdir(p("/ab"));
        fs.move(p("/a"), p("/ab/a"));
        assertTrue(fs.stat(p("/ab/a")) instanceof DirectoryNode);
    }

    @Test
    void renameInSameDirectoryDoesNotRestoreSource() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.createFile(p("/a/old"), "treść");
        fs.move(p("/a/old"), p("/a/new"));
        assertEquals(List.of(p("/a/new")), fs.list(p("/a")));
        assertEquals("treść", fs.read(p("/a/new")));
    }

    @Test
    void movingIntoAncestorPreservesUnrelatedSiblings() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.mkdir(p("/a/b"));
        fs.createFile(p("/a/b/f"), "x");
        fs.createFile(p("/keep"), "keep");
        fs.move(p("/a/b"), p("/moved"));
        assertEquals(List.of(), fs.list(p("/a")));
        assertEquals("x", fs.read(p("/moved/f")));
        assertEquals("keep", fs.read(p("/keep")));
    }

    @Test
    void badMoveDestinationNeverDetachesSource() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.createFile(p("/a/f"), "x");
        fs.createFile(p("/occupied"), "y");
        denied(fs, ALREADY_EXISTS, () -> fs.move(p("/a"), p("/occupied")));
        denied(fs, NOT_FOUND, () -> fs.move(p("/a"), p("/missing/a")));
        denied(fs, NOT_DIRECTORY, () -> fs.move(p("/a"), p("/occupied/a")));
        denied(fs, NOT_FOUND, () -> fs.move(p("/missing"), p("/new")));
        assertEquals("x", fs.read(p("/a/f")));
    }

    @Test
    void snapshotsAndInputMapsAreDeeplyImmutable() {
        var source = new TreeMap<String, FsNode>();
        source.put("f", new TextFile("x"));
        var dir = new DirectoryNode(source);
        source.clear();
        assertEquals(1, dir.children().size());
        assertThrows(UnsupportedOperationException.class, () -> dir.children().clear());
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/a"));
        fs.createFile(p("/a/f"), "old");
        var before = fs.snapshot();
        fs.write(p("/a/f"), "new");
        fs.move(p("/a"), p("/b"));
        var oldDirectory = (DirectoryNode) before.root().children().get("a");
        assertEquals("old", ((TextFile) oldDirectory.children().get("f")).content());
        assertThrows(UnsupportedOperationException.class, () -> fs.list(p("/")).clear());
        assertThrows(UnsupportedOperationException.class, () -> fs.find(p("/"), "b").clear());
    }

    @Test
    void searchIncludesStartAndMatchesNamesNotContentsInSortedOrder() {
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/docs"));
        fs.mkdir(p("/other"));
        fs.createFile(p("/docs/z-docs"), "");
        fs.createFile(p("/docs/A"), "docs");
        fs.createFile(p("/other/docs"), "");
        assertEquals(List.of(p("/docs"), p("/docs/z-docs")), fs.find(p("/docs"), "docs"));
        assertEquals(List.of(p("/docs"), p("/docs/z-docs"), p("/other/docs")), fs.find(p("/"), "docs"));
        assertEquals(List.of(p("/docs/A")), fs.find(p("/docs/A"), "A"));
        assertEquals(List.of(), fs.find(p("/"), "DOCS"));
        var before = fs.snapshot();
        assertThrows(IllegalArgumentException.class, () -> fs.find(p("/"), ""));
        assertSame(before, fs.snapshot());
    }

    @Test
    void emptyTextIsValidButNullWriteDoesNotChangeState() {
        var fs = new InMemoryFileSystem();
        fs.createFile(p("/f"), "");
        var before = fs.snapshot();
        assertThrows(NullPointerException.class, () -> fs.write(p("/f"), null));
        assertSame(before, fs.snapshot());
        assertEquals("", fs.read(p("/f")));
        denied(fs, NOT_FOUND, () -> fs.write(p("/missing"), "x"));
    }

    @Test
    void generatedMovesPreserveContentAndOldViewsAcrossManyRenames() {
        // Model spłaszczony do nazwy bieżącego katalogu i treści plików:
        // nie odtwarza algorytmu kopiowania przodków badanego drzewa.
        var random = new Random(130023);
        var fs = new InMemoryFileSystem();
        fs.mkdir(p("/left"));
        fs.mkdir(p("/right"));
        String current = "/left/start";
        fs.mkdir(p(current));
        var expected = new TreeMap<String, String>();
        for (int i = 0; i < 20; i++) {
            String name = "file" + i;
            String content = "text" + random.nextInt(1000);
            expected.put(name, content);
            fs.createFile(p(current + "/" + name), content);
        }
        var original = fs.snapshot();
        for (int i = 0; i < 100; i++) {
            String next = (i % 2 == 0 ? "/right/" : "/left/") + "step" + i;
            fs.move(p(current), p(next));
            denied(fs, NOT_FOUND, () -> fs.read(p("/missing")));
            final String old = current;
            denied(fs, NOT_FOUND, () -> fs.stat(p(old)));
            for (var entry : expected.entrySet()) {
                assertEquals(entry.getValue(), fs.read(p(next + "/" + entry.getKey())));
            }
            current = next;
        }
        var old = (DirectoryNode) ((DirectoryNode) original.root().children().get("left")).children().get("start");
        assertEquals(expected.keySet(), old.children().keySet());
    }

    /**
     * Każda odmowa musi zachować dokładnie ten sam opublikowany korzeń.
     */
    static void denied(InMemoryFileSystem fs, FileSystemException.Reason reason, Runnable command) {
        var before = fs.snapshot();
        assertEquals(reason, assertThrows(FileSystemException.class, command::run).reason());
        assertSame(before, fs.snapshot());
    }
}
