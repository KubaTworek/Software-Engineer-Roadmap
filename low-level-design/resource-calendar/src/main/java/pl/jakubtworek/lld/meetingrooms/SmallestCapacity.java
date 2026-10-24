package pl.jakubtworek.lld.meetingrooms;

import java.util.Comparator;
import java.util.List;

/**
 * Najmniejsza wystarczająca sala zostawia większe zasoby innym spotkaniom.
 * Wystarczalność sprawdzono przed wyborem, więc minimalizujemy pojemność bez
 * odejmowania liczby osób. Remis rozstrzyga kod. To lokalna heurystyka O(R),
 * nie optymalizator przyszłego kalendarza ani gwarancja globalnego maksimum spotkań.
 */
public final class SmallestCapacity implements RoomSelectionPolicy {

    @Override
    public MeetingRoom select(List<MeetingRoom> available) {
        return available.stream().min(Comparator.comparingInt(MeetingRoom::capacity).thenComparing(room -> room.id()
                .value())).orElseThrow();
    }
}
