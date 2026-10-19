package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProtocolAndTlsTest {

    @Test
    void http2MultiplexesRequestsWithinNegotiatedStreamLimit() {
        assertThat(ProtocolCapacity.requiredConnections(ProtocolCapacity.Protocol.HTTP_1_1, 100, 1)).isEqualTo(100);
        assertThat(ProtocolCapacity.requiredConnections(ProtocolCapacity.Protocol.HTTP_2, 100, 32)).isEqualTo(4);
    }

    @Test
    void protocolVersionAndResumptionChangeNetworkRoundTrips() {
        assertThat(TlsHandshakeCost.estimatedNetworkLatency(Duration.ofMillis(40), TlsHandshakeCost.Mode.TLS_1_2_FULL))
                .isEqualTo(Duration.ofMillis(80));
        assertThat(TlsHandshakeCost.estimatedNetworkLatency(Duration.ofMillis(40), TlsHandshakeCost.Mode.TLS_1_3_FULL))
                .isEqualTo(Duration.ofMillis(40));
        assertThat(TlsHandshakeCost.estimatedNetworkLatency(Duration.ofMillis(40), TlsHandshakeCost.Mode.TLS_1_3_ZERO_RTT))
                .isZero();
    }
}
