package pl.jakubtworek.backend_engineering.stage_1.block_g;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/** Odczyt cgroup v2; brak pomiaru pozostaje brakiem, nie fikcyjnym zerem. */
public record ProcessResourceSnapshot(long memoryCurrent, long memoryLimit,
                                     long oomKills, long throttledMicros) {
    public static ProcessResourceSnapshot read(Path cgroup) throws IOException {
        Map<String, Long> events = counters(cgroup.resolve("memory.events"));
        Map<String, Long> cpu = counters(cgroup.resolve("cpu.stat"));
        String limit = Files.readString(cgroup.resolve("memory.max")).trim();
        return new ProcessResourceSnapshot(number(cgroup.resolve("memory.current")),
                limit.equals("max") ? Long.MAX_VALUE : Long.parseLong(limit),
                events.getOrDefault("oom_kill", 0L), cpu.getOrDefault("throttled_usec", 0L));
    }

    public boolean newOomKillSince(ProcessResourceSnapshot before) {
        // Kod 137 oznacza SIGKILL; dopiero wzrost licznika dostarcza dowodu OOM w cgroup.
        return oomKills > before.oomKills;
    }

    public long throttledDelta(ProcessResourceSnapshot before) {
        if (throttledMicros < before.throttledMicros) {
            throw new IllegalArgumentException("Licznik wyzerowano: próbki nie są porównywalne");
        }
        return throttledMicros - before.throttledMicros;
    }

    private static long number(Path file) throws IOException {
        return Long.parseLong(Files.readString(file).trim());
    }

    private static Map<String, Long> counters(Path file) throws IOException {
        try (var lines = Files.lines(file)) {
            return lines.filter(line -> !line.isBlank()).map(line -> line.trim().split("\\s+"))
                    .collect(Collectors.toMap(parts -> parts[0], parts -> Long.parseLong(parts[1])));
        }
    }

    public static void main(String[] args) throws IOException {
        System.out.println(read(Path.of(args.length == 0 ? "/sys/fs/cgroup" : args[0])));
    }
}
