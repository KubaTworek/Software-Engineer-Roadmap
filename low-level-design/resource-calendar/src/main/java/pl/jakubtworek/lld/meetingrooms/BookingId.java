package pl.jakubtworek.lld.meetingrooms;

import java.util.Objects;
import java.util.UUID;

/**
 * ID konkretnej rezerwacji pozwala anulować właściwe spotkanie, także po ponownym zajęciu tego samego terminu.
 */
public record BookingId(UUID value) {

    public BookingId {
        Objects.requireNonNull(value, "value");
    }

    public static BookingId parse(String value) {
        return new BookingId(UUID.fromString(Objects.requireNonNull(value, "value").strip()));
    }
}
