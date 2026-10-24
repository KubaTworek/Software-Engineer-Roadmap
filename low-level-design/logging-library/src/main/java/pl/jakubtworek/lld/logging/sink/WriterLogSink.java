package pl.jakubtworek.lld.logging.sink;

import pl.jakubtworek.lld.logging.domain.LogSink;
import java.io.*;
import java.util.Objects;

/**
 * Adapter Writer dodaje jeden LF i flush. Nie jest właścicielem strumienia,
 * więc nie udostępnia close i nie zamyka System.out. Flush nie gwarantuje fsync.
 * Writer może zawieść po części zapisu. PrintWriter ukrywa IOException, dlatego
 * dodatkowo sprawdzamy jego znacznik błędu po operacji.
 */
public final class WriterLogSink implements LogSink {

    private final Writer writer;

    public WriterLogSink(Writer writer) {
        this.writer = Objects.requireNonNull(writer);
    }

    public void write(String formatted) throws IOException {
        writer.write(Objects.requireNonNull(formatted));
        writer.write("\n");
        writer.flush();
        if (writer instanceof PrintWriter printWriter && printWriter.checkError()) {
            throw new IOException("PrintWriter zgłosił błąd zapisu");
        }
    }
}
