package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Enforces the limit while streaming; Content-Length alone is not trusted. */
public final class BoundedBodyReader {

    public byte[] read(InputStream input, long declaredLength, int maximumBytes) throws IOException {
        if (input == null) throw new IllegalArgumentException("input is required");
        if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
        if (declaredLength > maximumBytes) throw new PayloadTooLargeException(maximumBytes);

        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8_192));
        byte[] buffer = new byte[Math.min(maximumBytes + 1, 8_192)];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximumBytes) throw new PayloadTooLargeException(maximumBytes);
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public static final class PayloadTooLargeException extends RuntimeException {
        public PayloadTooLargeException(int maximumBytes) {
            super("payload exceeds " + maximumBytes + " bytes");
        }
    }
}
