package pl.jakubtworek.lld.parkinglot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Rozszerzenie wymagań: wybierz najmniejsze pasujące miejsce, a dopiero potem
 * piętro i ID. To lokalna heurystyka zachowująca duże miejsca dla vanów,
 * nie optymalny plan całej przyszłej sekwencji wjazdów. Wszystkie kandydatury
 * są już zgodne z pojazdem. Test preservesLargeSpotForLaterVan pokazuje różnicę
 * względem first-available przy niezmienionym kodzie przyjmowania wjazdu.
 */
public final class BestFitAllocation implements SpotAllocationStrategy {

    @Override
    public Optional<ParkingSpot> select(FreeSpotIndex index, VehicleType type) {
        return index.bestFit(type);
    }

    @Override
    public Optional<ParkingSpot> select(List<ParkingSpot> candidates) {
        return candidates.stream().min(Comparator.comparingInt((ParkingSpot spot) -> spot.type().capacityRank())
                .thenComparingInt(ParkingSpot::floor).thenComparing(ParkingSpot::id));
    }
}
