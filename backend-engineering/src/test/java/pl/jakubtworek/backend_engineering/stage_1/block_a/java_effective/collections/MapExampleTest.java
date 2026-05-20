package pl.jakubtworek.backend_engineering.stage_1.block_a.java_effective.collections;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MapExampleTest {

    @Test
    void treeMapShouldBeSorted() {

        Map<Integer, String> map = MapExample.createTreeMap();

        map.put(3, "A");
        map.put(1, "B");
        map.put(2, "C");

        Integer firstKey = map.keySet().iterator().next();

        assertEquals(1, firstKey);
    }

    @Test
    void hashMapDoesNotGuaranteeOrder() {

        Map<Integer, String> map = MapExample.createHashMap();

        map.put(3, "A");
        map.put(1, "B");
        map.put(2, "C");

        assertEquals(3, map.size());
    }
}