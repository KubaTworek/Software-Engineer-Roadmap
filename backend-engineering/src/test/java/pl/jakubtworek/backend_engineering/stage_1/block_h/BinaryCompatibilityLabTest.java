package pl.jakubtworek.backend_engineering.stage_1.block_h;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BinaryCompatibilityLabTest {
    @TempDir Path directory;
    @Test void runtimeReplacementCanBreakAlreadyCompiledClient() throws Exception {
        var broken = BinaryCompatibilityLab.run(directory.resolve("broken"), false);
        assertNotEquals(0, broken.exitCode());
        assertTrue(broken.output().contains("NoSuchMethodError"), broken.output());
        var preserved = BinaryCompatibilityLab.run(directory.resolve("preserved"), true);
        assertEquals(0, preserved.exitCode());
        assertEquals("v2", preserved.output().trim());
    }
}
