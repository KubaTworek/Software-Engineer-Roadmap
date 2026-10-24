package pl.jakubtworek.lld.meetingrooms;

import java.util.List;
import java.util.Objects;

/**
 * Niemutowalna analiza jednego żądania. Nie obiecuje przyszłej dostępności:
 * kolejne polecenie może zmienić kalendarz. book i reschedule korzystają z tej
 * samej oceny co odczyt, więc prezentacja nie utrzymuje drugiej wersji reguł.
 * Przy zmianie terminu rooms obejmuje tylko dotychczasową salę, z pominięciem
 * zmienianego ID. Zwykłe wyszukiwanie obejmuje cały katalog.
 */
public record AvailabilityReport(RoomRequirements requirements, TimeSlot requestedSlot, TimeSlot blockingSlot,
        List<RoomAvailability> rooms) {

    public AvailabilityReport {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(requestedSlot, "requestedSlot");
        Objects.requireNonNull(blockingSlot, "blockingSlot");
        rooms = List.copyOf(rooms);
    }

    public List<MeetingRoom> availableRooms() {
        return rooms.stream().filter(RoomAvailability::available).map(RoomAvailability::room).toList();
    }
}
