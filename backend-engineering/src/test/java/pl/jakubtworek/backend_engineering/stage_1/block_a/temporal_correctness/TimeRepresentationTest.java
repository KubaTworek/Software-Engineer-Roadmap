package pl.jakubtworek.backend_engineering.stage_1.block_a.temporal_correctness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class TimeRepresentationTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private final LocalScheduleResolver resolver = new LocalScheduleResolver();

    @Test
    void oneStoredInstantCanBePresentedInDifferentUserZonesWithoutChangingTheEvent() {
        Instant stored = Instant.parse("2026-07-10T12:00:00Z");

        ZonedDateTime warsaw = TimeRepresentation.present(stored, WARSAW);
        ZonedDateTime newYork = TimeRepresentation.present(stored, ZoneId.of("America/New_York"));

        assertThat(warsaw.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-07-10T14:00:00"));
        assertThat(newYork.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-07-10T08:00:00"));
        assertThat(warsaw.toInstant()).isEqualTo(newYork.toInstant()).isEqualTo(stored);
    }

    @Test
    void missingDstTimeMustBeRejectedOrShiftedByAnExplicitPolicy() {
        LocalDateTime missing = LocalDateTime.parse("2026-03-29T02:30:00");

        assertThatThrownBy(() -> resolver.resolve(
                missing, WARSAW,
                LocalScheduleResolver.GapPolicy.REJECT,
                LocalScheduleResolver.OverlapPolicy.EARLIER_OFFSET))
                .isInstanceOf(DateTimeException.class);

        ZonedDateTime shifted = resolver.resolve(
                missing, WARSAW,
                LocalScheduleResolver.GapPolicy.SHIFT_FORWARD,
                LocalScheduleResolver.OverlapPolicy.EARLIER_OFFSET);

        assertThat(shifted.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-03-29T03:30:00"));
        assertThat(shifted.getOffset()).isEqualTo(ZoneOffset.ofHours(2));
    }

    @Test
    void repeatedDstTimeRepresentsTwoDifferentInstants() {
        LocalDateTime repeated = LocalDateTime.parse("2026-10-25T02:30:00");

        ZonedDateTime earlier = resolver.resolve(
                repeated, WARSAW,
                LocalScheduleResolver.GapPolicy.REJECT,
                LocalScheduleResolver.OverlapPolicy.EARLIER_OFFSET);
        ZonedDateTime later = resolver.resolve(
                repeated, WARSAW,
                LocalScheduleResolver.GapPolicy.REJECT,
                LocalScheduleResolver.OverlapPolicy.LATER_OFFSET);

        assertThat(earlier.getOffset()).isEqualTo(ZoneOffset.ofHours(2));
        assertThat(later.getOffset()).isEqualTo(ZoneOffset.ofHours(1));
        assertThat(Duration.between(earlier.toInstant(), later.toInstant())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void dailyLocalScheduleIsNotARepeatedTwentyFourHourDurationAcrossDst() {
        DailyBusinessSchedule schedule = new DailyBusinessSchedule(
                LocalTime.of(9, 0), WARSAW,
                LocalScheduleResolver.GapPolicy.SHIFT_FORWARD,
                LocalScheduleResolver.OverlapPolicy.EARLIER_OFFSET);
        Instant saturdayMorning = ZonedDateTime.of(
                LocalDateTime.parse("2026-03-28T09:00:00"), WARSAW).toInstant();

        Instant sundayMorning = schedule.nextAfter(saturdayMorning);

        assertThat(sundayMorning.atZone(WARSAW).toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(Duration.between(saturdayMorning, sundayMorning)).isEqualTo(Duration.ofHours(23));
    }
}
