package pl.jakubtworek.lld.filesystem.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Posortowane wejście degeneruje zwykłe BST; AVL musi utrzymać małą wysokość.
 */
class DirectoryIndexTest {

    @Test
    void sortedInsertionAndRandomRemovalMatchTreeMapWithoutChangingOldRoot() {
        var directory = DirectoryNode.empty();
        var model = new TreeMap<String, FsNode>();
        for (int i = 0; i < 4096; i++) {
            String key = String.format(Locale.ROOT, "n%05d", i);
            var file = new TextFile("v" + i);
            directory = directory.with(key, file);
            model.put(key, file);
        }
        assertTrue(directory.indexHeight() <= 18, "AVL nie może stać się łańcuchem");
        var old = directory;
        var oldExpected = new TreeMap<>(model);
        var keys = new ArrayList<>(model.keySet());
        Collections.shuffle(keys, new Random(131071));
        for (String key : keys) {
            assertEquals(model.remove(key), directory.child(key));
            directory = directory.without(key);
            if (model.size() % 128 == 0) {
                assertEquals(model, directory.children());
            }
        }
        assertTrue(directory.isEmpty());
        assertEquals(oldExpected, old.children());
    }
}
