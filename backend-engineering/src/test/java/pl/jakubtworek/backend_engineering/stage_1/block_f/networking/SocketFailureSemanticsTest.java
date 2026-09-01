package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SocketFailureSemanticsTest {

    @Test
    void establishedConnectionCanStillHitReadTimeout() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<Void> peer = new FutureTask<>(() -> {
                try (Socket ignored = server.accept()) {
                    Thread.sleep(Duration.ofMillis(300));
                }
                return null;
            });
            Thread.ofVirtual().start(peer);

            assertThatThrownBy(() -> SocketReadProbe.readByte(
                            "127.0.0.1", server.getLocalPort(), Duration.ofMillis(200), Duration.ofMillis(40)))
                    .isInstanceOf(SocketTimeoutException.class);
            peer.get(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void orderlyHalfCloseIsObservedAsEndOfStream() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<Void> peer = new FutureTask<>(() -> {
                try (Socket accepted = server.accept()) {
                    accepted.shutdownOutput();
                }
                return null;
            });
            Thread.ofVirtual().start(peer);

            assertThat(SocketReadProbe.readByte(
                            "127.0.0.1", server.getLocalPort(), Duration.ofMillis(200), Duration.ofMillis(200)))
                    .isEqualTo(-1);
            peer.get(1, TimeUnit.SECONDS);
        }
    }
}
