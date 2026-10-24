package pl.jakubtworek.lld.parkinglot;

import java.util.*;

/**
 * Indeks wolnych miejsc: trzy uporządkowane zbiory według typu.
 * Wbudowany wybór porównuje najwyżej trzy minima, bez skanu S miejsc i H biletów.
 * Aktualizacja zajętości O(log S). Pełna lista jest materializowana tylko dla
 * własnej strategii List, która faktycznie potrzebuje wszystkich kandydatów.
 */
public final class FreeSpotIndex {

    // TreeSet utożsamia elementy przy compare == 0. ID rozstrzyga remis pięter,
    // aby dwa różne miejsca na kondygnacji nie zlały się w jeden wpis.
    private static final Comparator<ParkingSpot> ORDER = Comparator.comparingInt(ParkingSpot::floor)
            .thenComparing(ParkingSpot::id);
    private final EnumMap<SpotType, NavigableSet<ParkingSpot>> free = new EnumMap<>(SpotType.class);

    FreeSpotIndex(List<ParkingSpot> spots) {
        for (var type : SpotType.values()) free.put(type, new TreeSet<>(ORDER));
        spots.forEach(this::release);
    }

    public Optional<ParkingSpot> firstAvailable(VehicleType vehicle) {
        ParkingSpot first = null;
        for (var type : SpotType.values()) {
            var set = free.get(type);
            if (type.accommodates(vehicle) && !set.isEmpty() && (first == null || ORDER.compare(set.first(),
                    first) < 0))
                first = set.first();
        }
        return Optional.ofNullable(first);
    }

    public Optional<ParkingSpot> bestFit(VehicleType vehicle) {
        ParkingSpot first = null;
        for (var type : SpotType.values()) {
            var set = free.get(type);
            if (type.accommodates(vehicle) && !set.isEmpty()) {
                var next = set.first();
                if (first == null || type.capacityRank() < first.type().capacityRank()
                        || type.capacityRank() == first.type().capacityRank() && ORDER.compare(next, first) < 0)
                    first = next;
            }
        }
        return Optional.ofNullable(first);
    }

    public List<ParkingSpot> candidates(VehicleType vehicle) {
        var result = new ArrayList<ParkingSpot>();
        for (var type : SpotType.values()) if (type.accommodates(vehicle))
            result.addAll(free.get(type));
        result.sort(ORDER);
        return List.copyOf(result);
    }

    boolean contains(ParkingSpot spot) {
        var set = free.get(spot.type());
        // Wyszukanie używa komparatora; equals sprawdza również pełną
        // wartość z katalogu, nie tylko równą pozycję w porządku drzewa.
        var actual = set.ceiling(spot);
        return actual != null && actual.equals(spot);
    }

    void occupy(ParkingSpot spot) {
        free.get(spot.type()).remove(spot);
    }

    void release(ParkingSpot spot) {
        free.get(spot.type()).add(spot);
    }
}
