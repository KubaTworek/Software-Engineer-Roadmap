package pl.jakubtworek.lld.parkinglot;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Skan i sortowanie stanowią niezależną wyrocznię dla dynamicznych indeksów.
 */
class FreeSpotIndexTest {

    @Test
    void indexedPoliciesMatchScanningOracleAfterRandomOccupancyChanges() {
        var all = new ArrayList<ParkingSpot>();
        for (int i = 0; i < 240; i++) {
            all.add(new ParkingSpot("P" + i, i % 7, SpotType.values()[i % 3]));
        }
        var index = new FreeSpotIndex(all);
        var free = new HashSet<>(all);
        var random = new Random(41217);
        Comparator<ParkingSpot> order = Comparator.comparingInt(ParkingSpot::floor).thenComparing(ParkingSpot::id);
        for (int step = 0; step < 3000; step++) {
            var spot = all.get(random.nextInt(all.size()));
            if (free.remove(spot)) {
                index.occupy(spot);
            } else {
                free.add(spot);
                index.release(spot);
            }
            for (VehicleType type : VehicleType.values()) {
                var candidates = free.stream().filter(value -> value.type().accommodates(type)).sorted(order).toList();
                assertEquals(new FirstAvailableAllocation().select(candidates), index.firstAvailable(type));
                assertEquals(new BestFitAllocation().select(candidates), index.bestFit(type));
            }
        }
    }
}
