package pl.jakubtworek.lld.parkinglot;

import java.util.List;
import java.util.Optional;

/**
 * Wariant podstawowy: pierwsze dostępne miejsce w porządku piętro, potem ID.
 * Listę w tym porządku przygotowuje ParkingLot. Prostota jest zaletą, lecz
 * samochód może zająć LARGE na parterze mimo wolnego COMPACT wyżej. To poprawne
 * dopasowanie, ale gorsza decyzja dla późniejszego vana — powód rozszerzenia.
 */
public final class FirstAvailableAllocation implements SpotAllocationStrategy {

    @Override
    public Optional<ParkingSpot> select(FreeSpotIndex index, VehicleType type) {
        return index.firstAvailable(type);
    }

    @Override
    public Optional<ParkingSpot> select(List<ParkingSpot> candidates) {
        return candidates.stream().findFirst();
    }
}
