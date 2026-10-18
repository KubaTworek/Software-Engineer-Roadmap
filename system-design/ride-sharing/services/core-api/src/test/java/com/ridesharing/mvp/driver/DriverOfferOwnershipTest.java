package com.ridesharing.mvp.driver;

import com.ridesharing.mvp.location.DriverLocationPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Dowodzi, że spóźniony callback starego ride nie zwolni cudzego kierowcy. */
class DriverOfferOwnershipTest {
    private final DriverRepository repository = mock(DriverRepository.class);
    private final DriverLocationPort locations = mock(DriverLocationPort.class);
    private final DriverService service = new DriverService(repository, locations);

    @Test
    void staleRideCannotReleaseDriverOwnedByAnotherRide() {
        UUID driverId = UUID.randomUUID();
        UUID currentRide = UUID.randomUUID();
        var driver = Driver.builder()
                .id(driverId)
                .availabilityStatus(DriverAvailabilityStatus.OFFERED_RIDE)
                .activeRideId(currentRide)
                .offerExpiresAt(Instant.now().plusSeconds(20))
                .build();
        when(repository.findWithLockById(driverId)).thenReturn(Optional.of(driver));

        assertThat(service.markAvailable(driverId, UUID.randomUUID())).isFalse();
        assertThat(driver.getAvailabilityStatus()).isEqualTo(DriverAvailabilityStatus.OFFERED_RIDE);
        verify(repository, never()).save(any());
    }

    @Test
    void matchingCannotReserveAlreadyOwnedDriver() {
        UUID driverId = UUID.randomUUID();
        var driver = Driver.builder()
                .id(driverId)
                .availabilityStatus(DriverAvailabilityStatus.OFFERED_RIDE)
                .activeRideId(UUID.randomUUID())
                .offerExpiresAt(Instant.now().plusSeconds(20))
                .build();
        when(repository.findWithLockById(driverId)).thenReturn(Optional.of(driver));

        assertThat(service.tryOfferDriver(driverId, UUID.randomUUID())).isFalse();
        verify(repository, never()).save(any());
    }
}
