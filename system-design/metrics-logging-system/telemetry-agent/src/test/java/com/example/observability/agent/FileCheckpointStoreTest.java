package com.example.observability.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileCheckpointStoreTest {
    @Test
    void persistsLastAcknowledgedOffset(@TempDir Path directory) throws Exception {
        var path = directory.resolve("agent.offset");
        new FileCheckpointStore(path).save(481L);
        assertEquals(481L, new FileCheckpointStore(path).load());
    }

    @Test
    void corruptCheckpointFailsSafeToReplay(@TempDir Path directory) throws Exception {
        var path = directory.resolve("agent.offset");
        java.nio.file.Files.writeString(path, "not-a-number");
        assertEquals(0L, new FileCheckpointStore(path).load());
    }
}
