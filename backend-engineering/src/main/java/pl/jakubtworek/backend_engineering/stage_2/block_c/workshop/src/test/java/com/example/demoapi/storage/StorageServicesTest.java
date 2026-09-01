package com.example.demoapi.storage;

import com.example.demoapi.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageServicesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void distinguishesDisposableAndPersistentMounts() throws IOException {
        AppProperties properties = properties();
        ScratchStorageService scratch = new ScratchStorageService(properties);
        PersistentDataService persistent = new PersistentDataService(properties);

        scratch.write("scratch.txt", "temporary");
        persistent.write("persistent.txt", "durable");

        assertThat(scratch.read("scratch.txt")).isEqualTo("temporary");
        assertThat(persistent.read("persistent.txt")).isEqualTo("durable");
        assertThat(scratch.read("missing.txt")).isEqualTo("<missing>");
    }

    @Test
    void rejectsPathTraversalOutsideMountedVolumes() {
        AppProperties properties = properties();
        ScratchStorageService scratch = new ScratchStorageService(properties);
        PersistentDataService persistent = new PersistentDataService(properties);

        assertThatThrownBy(() -> scratch.write("../outside.txt", "data"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> persistent.read("../../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.setScratchDirectory(temporaryDirectory.resolve("scratch").toString());
        properties.setPersistentDirectory(temporaryDirectory.resolve("persistent").toString());
        return properties;
    }
}
