package pl.jakubtworek.lld.meetingrooms.console;

import pl.jakubtworek.lld.meetingrooms.*;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Punkt składania zależności. Domyślnie używa rzeczywistego Clock i strategii
 * smallest; parametr first zmienia preferencję, a drugi argument podaje
 * nieujemny bufor po spotkaniu w minutach. Brak bufora oznacza zero.
 * Testy wstrzykują własny zegar bez polegania na dacie uruchomienia JAR.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) throws java.io.IOException {
        var out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        TurnaroundBuffer buffer;
        try {
            if (args.length > 2 || args.length > 0 && !Set.of("first", "smallest").contains(args[0])) {
                throw new IllegalArgumentException("Nieznana strategia lub nadmiar argumentów");
            }
            buffer = args.length == 2 ? new TurnaroundBuffer(Duration.ofMinutes(Long.parseLong(args[1]))) : TurnaroundBuffer.none();
        } catch (IllegalArgumentException | ArithmeticException error) {
            out.println("Użycie: java -jar resource-calendar-1.0.0-SNAPSHOT.jar [first|smallest] [BUFOR_MINUTY>=0]");
            return;
        }
        RoomSelectionPolicy policy = args.length > 0 && args[0]
                .equals("first") ? new FirstByCode() : new SmallestCapacity();
        var rooms = List.of(new MeetingRoom(new RoomId("A1"), "Audytorium", 20, Set.of(Equipment.PROJECTOR,
                Equipment.WHITEBOARD, Equipment.VIDEO_CONFERENCE)), new MeetingRoom(new RoomId("B1"), "Kameralna", 4,
                Set.of(Equipment.WHITEBOARD)), new MeetingRoom(new RoomId("C1"), "Warsztatowa", 8,
                Set.of(Equipment.PROJECTOR, Equipment.WHITEBOARD)));
        var service = new BookingService(rooms, Clock.systemUTC(), policy, buffer);
        new BookingConsole(service, new InputStreamReader(System.in, StandardCharsets.UTF_8), out).run();
    }
}
