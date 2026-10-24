package pl.jakubtworek.lld.meetingrooms;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.meetingrooms.Fixtures.*;

/**
 * Sprawdzamy granice wartości, które muszą pozostać poprawne także poza kalendarzem.
 */
class ValueObjectsTest {

    @Test
    void identifiersNormalizeAndRejectInvalidCodes() {
        assertEquals(new RoomId("A1"), new RoomId(" a1 "));
        assertEquals(ALA, new UserId(" ala "));
        for (String value : List.of("", "a b", "ą", "x".repeat(33))) {
            assertThrows(IllegalArgumentException.class, () -> new RoomId(value));
            assertThrows(IllegalArgumentException.class, () -> new UserId(value));
        }
        var id = new BookingId(UUID.randomUUID());
        assertEquals(id, BookingId.parse(" " + id.value() + " "));
        assertThrows(IllegalArgumentException.class, () -> BookingId.parse("bad"));
        assertThrows(NullPointerException.class, () -> new RoomId(null));
        assertThrows(NullPointerException.class, () -> new UserId(null));
        assertThrows(NullPointerException.class, () -> new BookingId(null));
    }

    @Test
    void roomRequiresAllEquipmentButAllowsExtraEquipmentAndExactCapacity() {
        assertTrue(SMALL.satisfies(new RoomRequirements(4, Set.of(Equipment.WHITEBOARD))));
        assertFalse(SMALL.satisfies(people(5)));
        assertFalse(SMALL.satisfies(new RoomRequirements(2, Set.of(Equipment.WHITEBOARD, Equipment.PROJECTOR))));
        assertTrue(BIG.satisfies(new RoomRequirements(1, Set.of(Equipment.WHITEBOARD))));
        assertFalse(BIG.satisfies(people(Integer.MAX_VALUE)));
    }

    @Test
    void equipmentCopiesPreventChangingRoomAndMeetingAfterConstruction() {
        var equipment = new HashSet<>(Set.of(Equipment.PROJECTOR));
        var room = new MeetingRoom(new RoomId("X"), " Sala ", 1, equipment);
        var requirements = new RoomRequirements(1, equipment);
        equipment.clear();
        assertEquals("Sala", room.name());
        assertEquals(Set.of(Equipment.PROJECTOR), room.equipment());
        assertEquals(room.equipment(), requirements.equipment());
        assertThrows(UnsupportedOperationException.class, () -> room.equipment().clear());
        assertThrows(UnsupportedOperationException.class, () -> requirements.equipment().clear());
    }

    @Test
    void invalidRoomAndRequirementConfigurationsFailImmediately() {
        assertThrows(IllegalArgumentException.class, () -> people(0));
        assertThrows(IllegalArgumentException.class, () -> people(-1));
        assertThrows(IllegalArgumentException.class, () -> new MeetingRoom(new RoomId("X"), " ", 1, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new MeetingRoom(new RoomId("X"), "Sala", 0, Set.of()));
        assertThrows(NullPointerException.class, () -> new RoomRequirements(1, null));
        assertThrows(NullPointerException.class, () -> new MeetingRoom(new RoomId("X"), "Sala", 1, null));
    }
}
