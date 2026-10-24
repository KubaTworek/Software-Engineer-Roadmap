package pl.jakubtworek.lld.logging.console;

import pl.jakubtworek.lld.logging.domain.*;
import pl.jakubtworek.lld.logging.format.*;
import pl.jakubtworek.lld.logging.sink.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/**
 * Adapter z trzema trasami: pamięć, symulowane zawodne wyjście i Writer konsoli.
 * Konfiguracja loggera jest wymieniana po poprawnej konstrukcji. fail zmienia
 * wyłącznie zachowanie symulowanego sinka, nie semantykę samego loggera.
 * Strumienie należą do wywołującego; zakończenie EOF nie zamyka System.out.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    private static final class FailureSwitch {

        boolean failing;
    }

    public static void main(String[] args) {
        run(new InputStreamReader(System.in, StandardCharsets.UTF_8), new PrintWriter(System.out, true,
                StandardCharsets.UTF_8));
    }

    public static void run(Readable source, PrintWriter out) {
        var input = new Scanner(source);
        var memory = new MemoryLogSink();
        LogContext context = LogContext.empty();
        ContextMasker masker = ContextMasker.redact(Set.of("password", "token"), "***");
        var simulated = new MemoryLogSink();
        var failure = new FailureSwitch();
        LogSink unstable = text -> {
            if (failure.failing)
                throw new IOException("Symulowana awaria");
            simulated.write(text);
        };
        var console = new WriterLogSink(out);
        Clock clock = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);
        LogLevel threshold = LogLevel.INFO;
        LogFormatter formatter = new PlainTextFormatter();
        FailurePolicy policy = FailurePolicy.CONTINUE;
        Map<String, LogLevel> thresholds = Map.of("memory", LogLevel.TRACE, "unstable", LogLevel.WARN, "console",
                LogLevel.INFO);
        Logger logger = build(threshold, formatter, policy, thresholds, memory, unstable, console, clock, masker);
        out.println("Biblioteka logowania — projekt edukacyjny. help, q.");
        while (input.hasNextLine()) {
            String line = input.nextLine().stripLeading();
            if (line.isBlank())
                continue;
            if (line.equals("q"))
                break;
            String[] parts = line.split("\\s+", 2);
            String data = parts.length == 2 ? parts[1] : "";
            try {
                switch(parts[0]) {
                    case "log" ->
                        {
                            String[] fields = data.split("\\s+", 2);
                            if (fields.length != 2)
                                throw new IllegalArgumentException("log LEVEL MESSAGE");
                            var result = logger.log(LogLevel.valueOf(fields[0]), context, fields[1]);
                            result.deliveries().forEach(d -> out.println(d.target() + ": " + d.status() + d.failure()
                                    .map(f -> " " + f.exceptionType() + ": " + f.message().orElse("")).orElse("")));
                        }
                    case "context" ->
                        {
                            String[] fields = data.split("\\s+", 2);
                            if (fields.length != 2)
                                throw new IllegalArgumentException("context KEY VALUE");
                            context = context.with(fields[0], fields[1]);
                            out.println("Pole kontekstu ustawione");
                        }
                    case "unset" ->
                        {
                            var candidate = new HashMap<>(context.fields());
                            candidate.remove(single(data));
                            context = new LogContext(candidate);
                            out.println("OK");
                        }
                    case "mask" ->
                        {
                            String keys = single(data);
                            ContextMasker candidate = keys.equals("none") ? ContextMasker.identity() : ContextMasker.redact(Set.of(keys.split(",", -1)), "***");
                            logger = build(threshold, formatter, policy, thresholds, memory, unstable, console, clock,
                                    candidate);
                            masker = candidate;
                            out.println("Maskowanie zmienione");
                        }
                    case "buffer" ->
                        {
                            String[] fields = data.split("\\s+", -1);
                            if (fields.length != 2)
                                throw new IllegalArgumentException("buffer CAPACITY REJECT_NEW|DROP_OLDEST");
                            var candidate = new MemoryLogSink(Integer.parseInt(fields[0]),
                                    OverflowPolicy.valueOf(fields[1]));
                            logger = build(threshold, formatter, policy, thresholds, candidate, unstable, console,
                                    clock, masker);
                            memory = candidate;
                            out.println("Nowy bufor pamięciowy");
                        }
                    case "level" ->
                        {
                            var candidate = LogLevel.valueOf(single(data));
                            logger = build(candidate, formatter, policy, thresholds, memory, unstable, console, clock,
                                    masker);
                            threshold = candidate;
                            out.println("Próg: " + threshold);
                        }
                    case "format" ->
                        {
                            LogFormatter candidate = switch(single(data)) {
                                case "TEXT" ->
                                    new PlainTextFormatter();
                                case "JSON" ->
                                    new JsonLogFormatter();
                                default ->
                                    throw new IllegalArgumentException("format TEXT|JSON");
                            };
                            logger = build(threshold, candidate, policy, thresholds, memory, unstable, console, clock,
                                    masker);
                            formatter = candidate;
                            out.println("Format zmieniony");
                        }
                    case "policy" ->
                        {
                            var candidate = FailurePolicy.valueOf(single(data));
                            logger = build(threshold, formatter, candidate, thresholds, memory, unstable, console,
                                    clock, masker);
                            policy = candidate;
                            out.println("Polityka: " + policy);
                        }
                    case "target" ->
                        {
                            String[] fields = data.split("\\s+", -1);
                            if (fields.length != 2 || !thresholds.containsKey(fields[0]))
                                throw new IllegalArgumentException("target memory|unstable|console LEVEL");
                            var candidate = new HashMap<>(thresholds);
                            candidate.put(fields[0], LogLevel.valueOf(fields[1]));
                            logger = build(threshold, formatter, policy, candidate, memory, unstable, console, clock,
                                    masker);
                            thresholds = Map.copyOf(candidate);
                            out.println("Próg wyjścia zmieniony");
                        }
                    case "fail" ->
                        {
                            failure.failing = switch(single(data)) {
                                case "on" ->
                                    true;
                                case "off" ->
                                    false;
                                default ->
                                    throw new IllegalArgumentException("fail on|off");
                            };
                            out.println("Awaria: " + failure.failing);
                        }
                    default ->
                        {
                            if (!data.isEmpty())
                                throw new IllegalArgumentException("Komenda bez argumentów");
                            switch(parts[0]) {
                                case "buffer-info" ->
                                    out.println(memory.diagnostics());
                                case "context-clear" ->
                                    {
                                        context = LogContext.empty();
                                        out.println("OK");
                                    }
                                case "history" ->
                                    memory.records().forEach(text -> out.println("memory> " + text));
                                case "clear" ->
                                    {
                                        memory.clear();
                                        simulated.clear();
                                        out.println("OK");
                                    }
                                case "help" ->
                                    out.println("log LEVEL MESSAGE; level LEVEL; target memory|unstable|console LEVEL; format TEXT|JSON; policy CONTINUE|STOP_ON_FAILURE; fail on|off; history; clear; context KEY VALUE; unset KEY; context-clear; mask KEY1,KEY2|none; buffer CAPACITY REJECT_NEW|DROP_OLDEST; buffer-info; q");
                                default ->
                                    throw new IllegalArgumentException("Nieznana komenda; help");
                            }
                        }
                }
            } catch (IllegalArgumentException ex) {
                out.println("Odmowa: " + ex.getMessage());
            }
        }
        out.flush();
    }

    private static Logger build(LogLevel threshold, LogFormatter formatter, FailurePolicy policy, Map<String,
            LogLevel> levels, LogSink memory, LogSink unstable, LogSink console, Clock clock, ContextMasker masker) {
        return new Logger("demo", threshold, formatter, List.of(new LogTarget("memory", levels.get("memory"), memory),
                new LogTarget("unstable", levels.get("unstable"), unstable), new LogTarget("console",
                levels.get("console"), console)), policy, clock, masker);
    }

    private static String single(String text) {
        if (text.isBlank() || text.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("Wymagany jeden argument");
        return text;
    }
}
