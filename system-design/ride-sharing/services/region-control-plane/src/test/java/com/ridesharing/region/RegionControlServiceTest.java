package com.ridesharing.region;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RegionControlServiceTest {
    private final RegionControlService service = new RegionControlService("eu-central");

    @Test
    void clientHintCannotMoveWriteOwnership() {
        var decision = service.route("ride-1", "warsaw", "us-east");
        assertThat(decision.homeRegion()).isEqualTo("eu-central");
    }

    @Test
    void leaseEpochFencesPreviousOwner() {
        var first = service.acquire("ride-1", "eu-central");
        assertThat(service.acceptsWrite("ride-1", "eu-central", first.epoch())).isTrue();
        assertThatThrownBy(() -> service.acquire("ride-1", "us-east"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.acceptsWrite("ride-1", "us-east", first.epoch())).isFalse();
    }
}
