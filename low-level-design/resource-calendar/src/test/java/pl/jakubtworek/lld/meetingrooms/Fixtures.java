package pl.jakubtworek.lld.meetingrooms;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * Wspólne dane scenariuszy; zegar zmieniamy jawnie, bez oczekiwania na rzeczywisty upływ czasu.
 */
public final class Fixtures {

    public static final Instant NOW = Instant.parse("2030-01-01T08:00:00Z");
    public static final UserId ALA = new UserId("ALA");
    public static final UserId BARTEK = new UserId("BARTEK");
    public static final MeetingRoom BIG = new MeetingRoom(new RoomId("A1"), "Duża", 20, Set.of(Equipment.PROJECTOR,
            Equipment.WHITEBOARD));
    public static final MeetingRoom SMALL = new MeetingRoom(new RoomId("B1"), "Mała", 4, Set.of(Equipment.WHITEBOARD));
    public static final MeetingRoom MEDIUM = new MeetingRoom(new RoomId("C1"), "Średnia", 8, Set.of(Equipment.PROJECTOR,
            Equipment.WHITEBOARD));
    public static final List<MeetingRoom> ROOMS = List.of(BIG, SMALL, MEDIUM);

    private Fixtures() {
    }

    public static TimeSlot slot(int from, int to) {
        return new TimeSlot(NOW.plusSeconds(from * 3600L), NOW.plusSeconds(to * 3600L));
    }

    public static RoomRequirements people(int count) {
        return new RoomRequirements(count, Set.of());
    }

    public static BookingService service(Clock clock) {
        return new BookingService(ROOMS, clock, new SmallestCapacity());
    }

    /**
     * Zmienny zegar UTC; withZone tworzy stały widok bieżącej chwili na potrzeby tych testów.
     */
    public static final class MutableClock extends Clock {

        private Instant now = NOW;

        public void at(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
