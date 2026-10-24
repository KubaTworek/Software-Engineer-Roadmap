package pl.jakubtworek.lld.meetingrooms;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.meetingrooms.Fixtures.*;
import static pl.jakubtworek.lld.meetingrooms.BookingException.Reason.*;

/**
 * Cztery ustalone ziarna i dwa bufory dają 2000 komend, nie dowód wszystkich kalendarzy.
 * Wyrocznia zajmuje całe komórki godzinowe zamiast używać TimeSlot.overlaps.
 * Oddzielne testy przedziałów sprawdzają nanosekundy i granice reprezentacji.
 */
class GeneratedCalendarTest {

    private record Model(RoomId room, int from, int to, UserId owner, boolean active) {
    }

    private record OldView(BookingSnapshot view, Map<BookingId, Booking> content) {
    }

    @ParameterizedTest
    @CsvSource({ "7,0", "31,0", "2026,0", "104729,0", "7,1", "31,1", "2026,1", "104729,1" })
    void generatedBookingsCancellationsMovesAndTimeChangesMatchOccupiedCellModel(long seed, int bufferHours) {
        var random = new Random(seed);
        var clock = new MutableClock();
        var service = new BookingService(ROOMS, clock, new SmallestCapacity(),
                new TurnaroundBuffer(Duration.ofHours(bufferHours)));
        var model = new HashMap<BookingId, Model>();
        var oldViews = new ArrayList<OldView>();
        int nowHour = 0;
        int accepted = 0;
        int denied = 0;
        int moves = 0;
        for (int step = 0; step < 250; step++) {
            if (step % 50 == 0) {
                nowHour++;
                clock.at(NOW.plusSeconds(nowHour * 3600L));
            }
            var before = service.snapshot();
            oldViews.add(new OldView(before, Map.copyOf(before.bookings())));
            int action = random.nextInt(4);
            if (!model.isEmpty() && action < 2) {
                // Pobieramy ID z listy posortowanej, żeby hash UUID nie zmieniał
                // kolejności wyborów przy ponownym uruchomieniu z tym samym ziarnem.
                // Sortujemy po przydzielonym numerze spotkania zapisanym w subject,
                // gdyż same UUID są celowo losowe.
                var ids = service.snapshot().bookings().values().stream().sorted(Comparator.comparing(Booking::subject))
                        .map(Booking::id).toList();
                var id = ids.get(random.nextInt(ids.size()));
                var entry = model.get(id);
                var actor = random.nextBoolean() ? entry.owner() : BARTEK;
                var expected = !actor.equals(entry.owner()) ? NOT_ORGANIZER : !entry.active() ? NOT_CONFIRMED : nowHour >= entry.from() ? ALREADY_STARTED : null;
                if (action == 0) {
                    if (expected != null) {
                        assertEquals(expected, assertThrows(BookingException.class, () -> service.cancel(id, actor))
                                .reason());
                        assertSame(before, service.snapshot());
                        denied++;
                    } else {
                        service.cancel(id, actor);
                        model.put(id, new Model(entry.room(), entry.from(), entry.to(), entry.owner(), false));
                        accepted++;
                    }
                } else {
                    int from = random.nextInt(24);
                    int to = from + 1 + random.nextInt(4);
                    if (expected == null) {
                        expected = from < nowHour ? PAST_START : occupied(model, entry.room(), from, to, bufferHours,
                                id) ? NO_AVAILABLE_ROOM : null;
                    }
                    if (expected != null) {
                        var reason = expected;
                        assertEquals(reason, assertThrows(BookingException.class, () -> service.reschedule(id, actor,
                                slot(from, to))).reason());
                        assertSame(before, service.snapshot());
                        denied++;
                    } else {
                        var moved = service.reschedule(id, actor, slot(from, to));
                        assertEquals(id, moved.id());
                        assertEquals(entry.room(), moved.roomId());
                        model.put(id, new Model(entry.room(), from, to, entry.owner(), true));
                        accepted++;
                        moves++;
                    }
                }
            } else {
                int from = random.nextInt(24);
                int to = from + 1 + random.nextInt(4);
                int count = 1 + random.nextInt(23);
                var equipment = random.nextBoolean() ? Set.of(Equipment.PROJECTOR) : Set.<Equipment>of();
                var requirements = new RoomRequirements(count, equipment);
                var candidate = ROOMS.stream().filter(room -> room.capacity() >= count && room.equipment()
                        .containsAll(equipment)).filter(room -> !occupied(model, room.id(), from, to, bufferHours,
                        null)).min(Comparator.comparingInt(MeetingRoom::capacity).thenComparing(room -> room.id()
                        .value()));
                var expected = from < nowHour ? PAST_START : candidate.isEmpty() ? NO_AVAILABLE_ROOM : null;
                String subject = "Spotkanie-" + step;
                if (expected != null) {
                    assertEquals(expected, assertThrows(BookingException.class, () -> service.book(ALA, requirements,
                            slot(from, to), subject)).reason());
                    assertSame(before, service.snapshot());
                    denied++;
                } else {
                    var booking = service.book(ALA, requirements, slot(from, to), subject);
                    assertEquals(candidate.orElseThrow().id(), booking.roomId());
                    assertNull(model.put(booking.id(), new Model(booking.roomId(), from, to, ALA, true)));
                    accepted++;
                }
            }
            assertEquals(model.size(), service.snapshot().bookings().size());
            model.forEach((id, expected) -> {
                var actual = service.snapshot().bookings().get(id);
                assertEquals(expected.room(), actual.roomId());
                assertEquals(slot(expected.from(), expected.to()), actual.slot());
                assertEquals(expected.active() ? BookingStatus.CONFIRMED : BookingStatus.CANCELLED, actual.status());
            });
        }
        assertTrue(accepted > 0 && denied > 0);
        assertTrue(moves > 0, "Każda próbka ma zawierać także udaną zmianę terminu");
        for (var old : oldViews) {
            assertEquals(old.content(), old.view().bookings());
        }
    }

    /**
     * Zajętość obliczamy przez wspólne komórki obu ROZSZERZONYCH terminów.
     * Wykluczamy tylko zmieniane ID; inne spotkanie tej samej osoby nadal blokuje.
     * Nie wywołujemy produkcyjnego overlaps ani TurnaroundBuffer.blockingSlot.
     */
    private static boolean occupied(Map<BookingId, Model> model, RoomId room, int from, int to, int buffer,
            BookingId ignored) {
        return IntStream.range(from, to + buffer).anyMatch(cell -> model.entrySet().stream().anyMatch(item -> {
            var entry = item.getValue();
            return !item.getKey().equals(ignored) && entry.active() && entry.room().equals(room)
                    && IntStream.range(entry.from(), entry.to() + buffer).anyMatch(taken -> taken == cell);
        }));
    }
}
