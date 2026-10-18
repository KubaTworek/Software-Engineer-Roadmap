package com.ridesharing.mlmatching;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingModelServiceTest {
    @Test
    void saferCloserCandidateRanksBeforeCancellationProneDriver() {
        var stable = new MatchingCandidate("stable", 1, 3, 4.9, 0.95, 0, true);
        var risky = new MatchingCandidate("risky", 5, 12, 4.1, 0.30, 8, false);

        var result = new MatchingModelService().rank(
                new MatchingRankRequest("ride-1", "warsaw", "standard", 1.0, List.of(risky, stable))
        );

        assertThat(result.rankedDrivers().getFirst().driverId()).isEqualTo("stable");
    }
}
