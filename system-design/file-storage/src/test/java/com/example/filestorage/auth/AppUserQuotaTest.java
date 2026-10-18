package com.example.filestorage.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dowodzi, ze rozpoczecie wielu uploadow nie omija limitu przez chunki tymczasowe. */
class AppUserQuotaTest {

    @Test
    void reservationConsumesQuotaBeforeUploadIsFinalized() {
        AppUser user = new AppUser("learner@example.test", "hash", "Learner");

        user.reserveUpload(AppUser.DEFAULT_STORAGE_QUOTA_BYTES);

        assertThatThrownBy(() -> user.reserveUpload(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quota");
        assertThat(user.getStorageReservedBytes()).isEqualTo(AppUser.DEFAULT_STORAGE_QUOTA_BYTES);
        assertThat(user.getStorageUsedBytes()).isZero();
    }

    @Test
    void committingReservationMovesBytesWithoutDoubleCounting() {
        AppUser user = new AppUser("learner@example.test", "hash", "Learner");
        user.reserveUpload(128);

        user.commitReservedStorage(128);

        assertThat(user.getStorageReservedBytes()).isZero();
        assertThat(user.getStorageUsedBytes()).isEqualTo(128);
    }
}
