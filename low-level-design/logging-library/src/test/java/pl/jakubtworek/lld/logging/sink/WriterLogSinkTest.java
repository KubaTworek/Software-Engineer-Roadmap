package pl.jakubtworek.lld.logging.sink;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.lld.logging.domain.*;
import pl.jakubtworek.lld.logging.format.PlainTextFormatter;
import java.io.*;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Writer może zawieść po zapisie prefiksu. Test pokazuje granicę częściowego
 * sukcesu zamiast obiecywać transakcję na niezależnych wyjściach.
 */
class WriterLogSinkTest {

    @Test
    void appendsOneLfFlushesAndNeverClosesBorrowedWriter() throws Exception {
        class Observed extends StringWriter {

            int flushes, closes;

            public void flush() {
                flushes++;
            }

            public void close() {
                closes++;
            }
        }
        var writer = new Observed();
        var sink = new WriterLogSink(writer);
        sink.write("first");
        sink.write("second");
        assertEquals("first\nsecond\n", writer.toString());
        assertEquals(2, writer.flushes);
        assertEquals(0, writer.closes);
    }

    @Test
    void partialWriteFailureIsReportedAndContinueStillTriesNext() {
        var partial = new StringBuilder();
        Writer broken = new Writer() {

            public void write(char[] text, int off, int length) throws IOException {
                partial.append(text[off]);
                throw new IOException("disk");
            }

            public void flush() {
            }

            public void close() {
            }
        };
        var next = new MemoryLogSink();
        var logger = new Logger("test", LogLevel.INFO, new PlainTextFormatter(), List.of(new LogTarget("writer",
                LogLevel.TRACE, new WriterLogSink(broken)), new LogTarget("next", LogLevel.TRACE, next)),
                FailurePolicy.CONTINUE, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var result = logger.log(LogLevel.INFO, "text");
        assertFalse(partial.isEmpty());
        assertEquals(Delivery.Status.FAILED, result.deliveries().getFirst().status());
        assertEquals(1, next.records().size());
    }

    @Test
    void printWriterHiddenFailureBecomesIOException() {
        Writer broken = new Writer() {

            public void write(char[] text, int off, int len) throws IOException {
                throw new IOException("io");
            }

            public void flush() {
            }

            public void close() {
            }
        };
        assertThrows(IOException.class, () -> new WriterLogSink(new PrintWriter(broken)).write("x"));
    }
}
