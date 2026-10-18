package com.example.videostreaming.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class VideoLifecycleTest {
    @Test
    void cannotPublishBeforeCompleteMasterManifestIsAttached() {
        Video video = new Video("System Design", "Educational movie", null);

        assertThatThrownBy(video::publish)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READY");
    }

    @Test
    void readyVideoCanBePublishedIdempotently() {
        Video video = new Video("System Design", "Educational movie", null);
        video.markReady("videos/id/hls/job/master.m3u8");

        video.publish();
        var firstPublishedAt = video.getPublishedAt();
        video.publish();

        assertThat(video.getStatus()).isEqualTo(VideoStatus.PUBLISHED);
        assertThat(video.getVisibility()).isEqualTo(VideoVisibility.PUBLIC);
        assertThat(video.getPublishedAt()).isEqualTo(firstPublishedAt);
    }
}
