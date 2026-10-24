package pl.jakubtworek.lld.meetingrooms;

import java.util.Comparator;
import java.util.List;

/**
 * Najmniejszy kod zapewnia powtarzalność niezależnie od kolejności katalogu; koszt O(R), nie potrzebujemy sortować kopii.
 */
public final class FirstByCode implements RoomSelectionPolicy {

    @Override
    public MeetingRoom select(List<MeetingRoom> available) {
        return available.stream().min(Comparator.comparing(room -> room.id().value())).orElseThrow();
    }
}
