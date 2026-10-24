package pl.jakubtworek.lld.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RopeTextTest {

    /**
     * Płaski StringBuilder jest niezależną wyrocznią dla split/join i rotacji.
     */
    @Test
    void generatedEditsAndSlicesAgreeWithFlatTextAndPreserveOldRoots() {
        var random = new Random(120024);
        RopeText rope = RopeText.of("początek🙂");
        var model = new StringBuilder(rope.toString());
        var old = new ArrayList<RopeText>();
        var texts = new ArrayList<String>();
        for (int step = 0; step < 7000; step++) {
            if (step % 100 == 0) {
                old.add(rope);
                texts.add(model.toString());
            }
            int offset = random.nextInt(model.length() + 1);
            int removed = random.nextInt(model.length() - offset + 1);
            String inserted = ("żółw-" + step).repeat(random.nextInt(100));
            rope = rope.replace(offset, removed, inserted);
            model.replace(offset, offset + removed, inserted);
            assertEquals(model.toString(), rope.toString(), "krok " + step);
            assertTrue(rope.balanced(), "AVL po kroku " + step);
            if (!model.isEmpty()) {
                int index = random.nextInt(model.length());
                assertEquals(model.charAt(index), rope.charAt(index));
            }
            int start = random.nextInt(model.length() + 1);
            int end = start + random.nextInt(model.length() - start + 1);
            assertEquals(model.substring(start, end), rope.subSequence(start, end).toString());
        }
        for (int i = 0; i < old.size(); i++) {
            assertEquals(texts.get(i), old.get(i).toString());
        }
    }

    /**
     * Posortowane dopisania nie mogą utworzyć łańcucha o wysokości liczby edycji.
     */
    @Test
    void tenThousandAppendsHaveLogarithmicHeight() {
        RopeText rope = RopeText.of("");
        for (int i = 0; i < 10000; i++) {
            rope = rope.replace(rope.length(), 0, "abcdefghijklmnop");
            if (i % 100 == 0) {
                assertTrue(rope.balanced());
            }
        }
        assertEquals(160000, rope.length());
        int bound = 2 * (32 - Integer.numberOfLeadingZeros(rope.leaves()));
        assertTrue(rope.height() <= bound);
        assertTrue(rope.balanced());
        assertEquals("abcdefghijklmnop".repeat(10000), rope.toString());
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 255, 256, 257, 511, 512, 513, 4097 })
    void splittingAtChunkBoundariesAndDeletingEverything(int length) {
        String value = "a".repeat(length);
        RopeText rope = RopeText.of(value);
        assertEquals(value, rope.toString());
        for (int position : new int[] { 0, length / 2, length }) {
            var edited = rope.replace(position, 0, "X");
            assertEquals(value.substring(0, position) + "X" + value.substring(position), edited.toString());
            assertTrue(edited.balanced());
        }
        assertEquals("", rope.replace(0, length, "").toString());
    }

    @Test
    void invalidRangesCannotMutateOldText() {
        var text = RopeText.of("abc");
        assertThrows(IndexOutOfBoundsException.class, () -> text.replace(-1, 0, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> text.replace(1, Integer.MAX_VALUE, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> text.subSequence(2, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> text.charAt(3));
        assertThrows(NullPointerException.class, () -> text.replace(0, 0, null));
        assertEquals("abc", text.toString());
        assertSame(text, text.replace(1, 0, ""));
    }
}
