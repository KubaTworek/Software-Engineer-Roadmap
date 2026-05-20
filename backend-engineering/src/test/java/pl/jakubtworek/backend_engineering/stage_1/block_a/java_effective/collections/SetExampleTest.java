package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.collections;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SetExampleTest {

    @Test
    void hashSetShouldIgnoreDuplicates() {

        Set<Integer> set = SetExample.createHashSet();

        set.add(1);
        set.add(1);
        set.add(2);

        assertEquals(2, set.size());
    }

    @Test
    void treeSetShouldBeSorted() {

        Set<Integer> set = SetExample.createTreeSet();

        set.add(3);
        set.add(1);
        set.add(2);

        assertEquals(1, set.iterator().next());
    }
}