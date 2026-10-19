package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import java.time.Duration;

/** Educational latency model; protocol version, cipher and CPU still affect real results. */
public final class TlsHandshakeCost {

    public enum Mode {
        TLS_1_2_FULL(2),
        TLS_1_3_FULL(1),
        TLS_1_3_RESUMED(1),
        TLS_1_3_ZERO_RTT(0);

        private final int networkRoundTrips;

        Mode(int networkRoundTrips) {
            this.networkRoundTrips = networkRoundTrips;
        }
    }

    private TlsHandshakeCost() {}

    public static Duration estimatedNetworkLatency(Duration roundTripTime, Mode mode) {
        if (roundTripTime == null || roundTripTime.isNegative()) {
            throw new IllegalArgumentException("roundTripTime cannot be negative");
        }
        if (mode == null) throw new IllegalArgumentException("TLS mode is required");
        return roundTripTime.multipliedBy(mode.networkRoundTrips);
    }
}
