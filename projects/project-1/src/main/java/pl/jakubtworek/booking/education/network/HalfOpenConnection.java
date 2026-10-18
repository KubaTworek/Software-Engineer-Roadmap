package pl.jakubtworek.booking.education.network;

import java.io.IOException;

/**
 * Symulacja half-open TCP: lokalny proces nadal uważa socket za otwarty, chociaż
 * peer zniknął bez poprawnego FIN/RST. Sam checkout z puli nie dowodzi żywotności;
 * błąd ujawnia dopiero I/O albo aktywny probe/keepalive.
 */
public final class HalfOpenConnection {
    private boolean locallyOpen = true;
    private boolean remoteClosedSilently;

    public void remoteDisappearsWithoutCloseFrame() {
        remoteClosedSilently = true;
    }

    public boolean isLocallyOpen() {
        return locallyOpen;
    }

    public void write(byte[] payload) throws IOException {
        if (!locallyOpen) {
            throw new IOException("local socket is closed");
        }
        if (remoteClosedSilently) {
            locallyOpen = false;
            throw new IOException("connection reset discovered during I/O");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
    }
}
