package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

/** Real socket probe that keeps connect and read timeouts as separate controls. */
public final class SocketReadProbe {

    private SocketReadProbe() {}

    public static int readByte(String host, int port, Duration connectTimeout, Duration readTimeout) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), toMillis(connectTimeout));
            socket.setSoTimeout(toMillis(readTimeout));
            return socket.getInputStream().read();
        }
    }

    private static int toMillis(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return Math.toIntExact(timeout.toMillis());
    }
}
