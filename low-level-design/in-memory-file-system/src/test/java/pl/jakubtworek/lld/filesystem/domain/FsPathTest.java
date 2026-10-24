package pl.jakubtworek.lld.filesystem.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Odrzucenie aliasów ma być jawne: domena nie udaje reguł POSIX ani Windows.
 */
class FsPathTest {

    @ParameterizedTest
    @ValueSource(strings = { "", "a", "a/b", "//", "/a/", "/a//b", "/.", "/..", "/a/../b", "/a/./b", "/a\\b", "/ ",
            "/a\nb", "/a\u0000b" })
    void rejectsNonCanonicalPaths(String path) {
        assertThrows(IllegalArgumentException.class, () -> FsPath.parse(path));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/", "/a", "/a/b", "/Zażółć gęślą", "/a.txt", "/ A " })
    void preservesCanonicalNamesExactly(String value) {
        assertEquals(value, FsPath.parse(value).toString());
    }

    @Test
    void prefixUsesWholeSegmentsAndRootHasNoParent() {
        var a = FsPath.parse("/a");
        assertTrue(a.contains(FsPath.parse("/a/b")));
        assertTrue(a.contains(a));
        assertFalse(a.contains(FsPath.parse("/ab")));
        assertTrue(FsPath.parse("/").contains(a));
        assertThrows(IllegalArgumentException.class, () -> FsPath.parse("/").parent());
    }

    @Test
    void sourceSegmentsCannotMutatePath() {
        var names = new ArrayList<>(List.of("a"));
        var path = new FsPath(names);
        names.clear();
        assertEquals("/a", path.toString());
        assertThrows(UnsupportedOperationException.class, () -> path.segments().clear());
    }
}
