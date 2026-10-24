package pl.jakubtworek.lld.logging.domain;

import java.io.IOException;

/**
 * Granica efektu ubocznego. Powrót oznacza zakończenie wywołania, nie trwały
 * zapis na dysku. IOException lub RuntimeException mogą wystąpić po częściowym
 * zapisie; caller nie powinien automatycznie ponawiać całej operacji fan-out.
 */
@FunctionalInterface
public interface LogSink {

    void write(String formatted) throws IOException;
}
