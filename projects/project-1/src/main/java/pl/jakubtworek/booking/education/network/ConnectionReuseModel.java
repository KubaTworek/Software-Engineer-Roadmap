package pl.jakubtworek.booking.education.network;

/**
 * Modeluje koszt tworzenia połączeń w jednym równoległym burstcie. Każde nowe
 * połączenie HTTPS wymaga TCP connect i TLS handshake. Keep-alive amortyzuje ten
 * koszt, a HTTP/2 może dodatkowo multipleksować wiele requestów na jednym
 * połączeniu. Model nie wycenia CPU kryptografii; liczy granicę zasobów.
 */
public record ConnectionReuseModel(boolean keepAlive, int poolSize, int http2MaxConcurrentStreams) {
    public ConnectionReuseModel {
        if (poolSize < 1 || http2MaxConcurrentStreams < 1) {
            throw new IllegalArgumentException("poolSize and stream limit must be positive");
        }
    }

    public int tlsHandshakesForBurst(int parallelRequests, HttpProtocol protocol) {
        if (parallelRequests < 0) {
            throw new IllegalArgumentException("parallelRequests cannot be negative");
        }
        if (parallelRequests == 0) {
            return 0;
        }
        if (!keepAlive) {
            return parallelRequests;
        }
        return switch (protocol) {
            case HTTP_1_1 -> Math.min(parallelRequests, poolSize);
            case HTTP_2 -> Math.min(poolSize, ceilingDivision(parallelRequests, http2MaxConcurrentStreams));
        };
    }

    /** Liczba krótkich połączeń jest też dolną granicą zużytych portów ephemeral. */
    public int openedConnections(int requests, HttpProtocol protocol) {
        return tlsHandshakesForBurst(requests, protocol);
    }

    private static int ceilingDivision(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    public enum HttpProtocol {
        HTTP_1_1,
        HTTP_2
    }
}
