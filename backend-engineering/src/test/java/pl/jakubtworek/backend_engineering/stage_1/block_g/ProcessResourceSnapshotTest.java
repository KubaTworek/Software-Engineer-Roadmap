package pl.jakubtworek.backend_engineering.stage_1.block_g;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ProcessResourceSnapshotTest {
    @TempDir Path cgroup;

    @Test void readsUnlimitedMemoryAndDistinguishesOomEvidenceFromExitCode() throws Exception {
        Files.writeString(cgroup.resolve("memory.max"), "max\n");
        Files.writeString(cgroup.resolve("memory.current"), "1000\n");
        Files.writeString(cgroup.resolve("memory.events"), "oom 2\noom_kill 0\n");
        Files.writeString(cgroup.resolve("cpu.stat"), "usage_usec 50\nthrottled_usec 12\n");
        var before = ProcessResourceSnapshot.read(cgroup);
        assertEquals(Long.MAX_VALUE, before.memoryLimit());
        assertFalse(before.newOomKillSince(before));
        Files.writeString(cgroup.resolve("memory.events"), "oom 3\noom_kill 1\n");
        Files.writeString(cgroup.resolve("cpu.stat"), "throttled_usec 42\n");
        var after = ProcessResourceSnapshot.read(cgroup);
        assertTrue(after.newOomKillSince(before));
        assertEquals(30, after.throttledDelta(before));
        assertThrows(IllegalArgumentException.class, () -> before.throttledDelta(after));
    }

    @Test void missingObservationFailsInsteadOfReportingHealthyZero() {
        assertThrows(java.io.IOException.class, () -> ProcessResourceSnapshot.read(cgroup));
    }
}
