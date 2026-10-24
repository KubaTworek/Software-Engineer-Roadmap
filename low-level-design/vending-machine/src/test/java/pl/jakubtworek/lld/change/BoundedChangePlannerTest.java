package pl.jakubtworek.lld.change;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BoundedChangePlannerTest {

    /**
     * Enumeracja sztuk jest celowo wolna i niezależna od kolejki monotonicznej.
     */
    @Test
    void genericCoreAgreesWithEnumerationIncludingUnsortedAndRepeatedDenominations() {
        var planner = new BoundedChangePlanner(100);
        var random = new Random(220024);
        for (int sample = 0; sample < 3000; sample++) {
            int[] values = { 1 + random.nextInt(20), 1 + random.nextInt(20), 1 + random.nextInt(20) };
            int[] counts = { random.nextInt(5), random.nextInt(5), random.nextInt(5) };
            int amount = random.nextInt(101), minimum = Integer.MAX_VALUE;
            for (int a = 0; a <= counts[0]; a++) {
                for (int b = 0; b <= counts[1]; b++) {
                    for (int c = 0; c <= counts[2]; c++) {
                        if (a * values[0] + b * values[1] + c * values[2] == amount) {
                            minimum = Math.min(minimum, a + b + c);
                        }
                    }
                }
            }
            var actual = planner.plan(amount, values, counts);
            assertEquals(minimum != Integer.MAX_VALUE, actual.isPresent());
            if (actual.isPresent()) {
                int[] plan = actual.orElseThrow();
                int total = 0, used = 0;
                for (int i = 0; i < 3; i++) {
                    assertTrue(plan[i] >= 0 && plan[i] <= counts[i]);
                    total += plan[i] * values[i];
                    used += plan[i];
                }
                assertEquals(amount, total);
                assertEquals(minimum, used);
            }
        }
    }

    @Test
    void limitsAndInputArraysRemainProtected() {
        var planner = new BoundedChangePlanner(100);
        assertThrows(IllegalArgumentException.class, () -> planner.plan(101, new int[] { 1 }, new int[] { 1 }));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(1, new int[] { 0 }, new int[] { 1 }));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(1, new int[] { 1 }, new int[] { -1 }));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(1, new int[] { 1 }, new int[] {}));
        int[] values = { Integer.MAX_VALUE, 1 }, counts = { Integer.MAX_VALUE, Integer.MAX_VALUE };
        assertArrayEquals(new int[] { 0, 100 }, planner.plan(100, values, counts).orElseThrow());
        assertArrayEquals(new int[] { Integer.MAX_VALUE, 1 }, values);
        assertArrayEquals(new int[] { Integer.MAX_VALUE, Integer.MAX_VALUE }, counts);
        assertArrayEquals(new int[0], planner.plan(0, new int[0], new int[0]).orElseThrow());
        assertTrue(planner.plan(1, new int[0], new int[0]).isEmpty());
    }
}
