package pl.jakubtworek.lld.editor;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EditorHistoryTest {

    @Test
    void editingAfterUndoKeepsBothRedoBranchesAndSnapshots() {
        var editor = new EditorHistory("A");
        var first = editor.insert(1, "B");
        var second = editor.insert(2, "C");
        assertSame(first, editor.undo());
        var alternate = editor.insert(2, "X");
        editor.undo();
        assertEquals(List.of(second.version(), alternate.version()), editor.branches().stream().map(RevisionInfo::id)
                .toList());
        assertSame(alternate, editor.redo());
        editor.undo();
        assertSame(second, editor.redo(second.version()));
        assertEquals("ABC", second.text().toString());
        assertEquals("ABX", alternate.text().toString());
        assertEquals("AB", first.text().toString());
    }

    @Test
    void checkoutAndUndoFollowParentOfSelectedBranch() {
        var editor = new EditorHistory("");
        var a = editor.insert(0, "A");
        var b = editor.insert(1, "B");
        editor.checkout(0);
        var x = editor.insert(0, "X");
        editor.checkout(b.version());
        assertSame(a, editor.undo());
        assertSame(b, editor.redo());
        editor.checkout(x.version());
        assertEquals(0, editor.undo().version());
        assertSame(x, editor.redo());
    }

    @Test
    void refusalPreservesCursorBranchesAndVersionCounter() {
        var editor = new EditorHistory("abc");
        var before = editor.snapshot();
        assertThrows(NoSuchElementException.class, editor::undo);
        assertThrows(NoSuchElementException.class, editor::redo);
        assertThrows(IllegalArgumentException.class, () -> editor.redo(123));
        assertThrows(IllegalArgumentException.class, () -> editor.checkout(123));
        assertThrows(IllegalStateException.class, () -> editor.replace(999, 0, 1, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> editor.delete(3, 1));
        assertThrows(NullPointerException.class, () -> editor.insert(0, null));
        assertSame(before, editor.snapshot());
        assertEquals(1, editor.versionCount());
        assertTrue(editor.branches().isEmpty());
        assertSame(before, editor.insert(1, ""));
        assertEquals(1, editor.insert(3, "!").version());
        assertThrows(IllegalStateException.class, () -> editor.replace(before.version(), 0, 1, "x"));
    }

    @Test
    void branchReportsAreImmutableCopiesAndIdenticalReplacementIsAnEdit() {
        var editor = new EditorHistory("a");
        var empty = editor.branches();
        var next = editor.replace(0, 0, 1, "a");
        assertEquals(1, next.version());
        editor.undo();
        var branches = editor.branches();
        assertThrows(UnsupportedOperationException.class, branches::clear);
        editor.insert(0, "b");
        assertTrue(empty.isEmpty());
        assertEquals(1, branches.size());
        assertEquals(3, editor.versionCount());
    }

    /**
     * Model przechowuje pełne String każdej wersji; nie odtwarza rope ani rotacji.
     */
    @Test
    void generatedBranchingHistoryAgreesWithIndependentVersionTree() {
        record Cell(String text, Long parent) {
        }
        var editor = new EditorHistory("");
        var cells = new LinkedHashMap<Long, Cell>();
        var children = new HashMap<Long, List<Long>>();
        var preferred = new HashMap<Long, Long>();
        cells.put(0L, new Cell("", null));
        children.put(0L, new ArrayList<>());
        long current = 0, sequence = 0;
        var random = new Random(120025);
        for (int step = 0; step < 8000; step++) {
            Cell cell = cells.get(current);
            switch(random.nextInt(5)) {
                case 0, 1 ->
                    {
                        int offset = random.nextInt(cell.text().length() + 1);
                        int removed = random.nextInt(cell.text().length() - offset + 1);
                        String inserted = "s" + step;
                        long next = ++sequence;
                        String text = cell.text().substring(0, offset) + inserted + cell.text()
                                .substring(offset + removed);
                        editor.replace(current, offset, removed, inserted);
                        cells.put(next, new Cell(text, current));
                        children.put(next, new ArrayList<>());
                        children.get(current).add(next);
                        preferred.put(current, next);
                        current = next;
                    }
                case 2 ->
                    {
                        if (cell.parent() == null) {
                            assertThrows(NoSuchElementException.class, editor::undo);
                        } else {
                            preferred.put(cell.parent(), current);
                            current = cell.parent();
                            editor.undo();
                        }
                    }
                case 3 ->
                    {
                        if (preferred.containsKey(current)) {
                            current = preferred.get(current);
                            editor.redo();
                        } else {
                            assertThrows(NoSuchElementException.class, editor::redo);
                        }
                    }
                case 4 ->
                    {
                        long selected = random.nextLong(sequence + 1);
                        current = selected;
                        editor.checkout(selected);
                    }
            }
            assertEquals(current, editor.snapshot().version());
            assertEquals(cells.get(current).text(), editor.snapshot().text().toString());
            assertEquals(children.get(current), editor.branches().stream().map(RevisionInfo::id).toList());
            assertEquals(cells.size(), editor.versionCount());
        }
    }
}
