package pl.jakubtworek.lld.meetingrooms;

import java.util.List;

/**
 * Strategy rozstrzyga preferencję dopiero PO odfiltrowaniu niedopuszczalnych
 * sal. Dostaje niepustą, niemutowalną listę i ma zwrócić jeden z jej elementów.
 * Kontrakt: czysta, deterministyczna funkcja, bez I/O i ponownego wejścia do
 * BookingService. Strategia nie zwalnia usługi z kontroli zwróconego wyniku.
 */
public interface RoomSelectionPolicy {

    MeetingRoom select(List<MeetingRoom> available);
}
