package com.example.videostreaming.transcoding;

import com.example.videostreaming.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class HlsArtifactPublisherTest {
    @TempDir Path directory;

    @Test
    void publishesSegmentsThenVariantPlaylistAndMasterAsCommitMarker() throws Exception {
        Path variant = Files.createDirectories(directory.resolve("360p"));
        Path segment = Files.writeString(variant.resolve("segment_00000.ts"), "media");
        Path playlist = Files.writeString(variant.resolve("index.m3u8"),
                "#EXTM3U\n#EXTINF:6,\nsegment_00000.ts\n#EXT-X-ENDLIST\n");
        Path master = Files.writeString(directory.resolve("master.m3u8"),
                "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=640x360\n360p/index.m3u8\n");

        ObjectStorageService storage = mock(ObjectStorageService.class);
        HlsArtifactPublisher publisher = new HlsArtifactPublisher(storage);

        publisher.publishCompletePackage(directory, "videos/v1/hls/job1");

        InOrder order = inOrder(storage);
        order.verify(storage).uploadFile("videos/v1/hls/job1/360p/segment_00000.ts", segment, "video/mp2t");
        order.verify(storage).uploadFile("videos/v1/hls/job1/360p/index.m3u8", playlist,
                "application/vnd.apple.mpegurl");
        order.verify(storage).uploadFile("videos/v1/hls/job1/master.m3u8", master,
                "application/vnd.apple.mpegurl");
    }

    @Test
    void rejectsManifestThatReferencesMissingSegmentBeforeAnyUpload() throws Exception {
        Path variant = Files.createDirectories(directory.resolve("720p"));
        Files.writeString(variant.resolve("index.m3u8"), "#EXTM3U\nmissing.ts\n");
        Files.writeString(directory.resolve("master.m3u8"), "#EXTM3U\n720p/index.m3u8\n");
        ObjectStorageService storage = mock(ObjectStorageService.class);

        assertThatThrownBy(() -> new HlsArtifactPublisher(storage)
                .publishCompletePackage(directory, "videos/v1/hls/job1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("segment");

        verifyNoInteractions(storage);
    }
}
