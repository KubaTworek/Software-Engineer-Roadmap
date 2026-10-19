package com.example.videostreaming.transcoding;

import java.util.List;

/**
 * Jeden szczebel drabiny ABR. Bitrate jest budżetem kodera, a nie obietnicą
 * jakości obrazu — jakość trzeba później oceniać np. VMAF-em dla konkretnego
 * materiału. Stała drabina upraszcza laboratorium; produkcyjnie profile mogą
 * być wybierane per-title po analizie źródła.
 */
public record HlsRenditionProfile(String name, int width, int height, int videoBitrateKbps) {

    public static List<HlsRenditionProfile> educationalLadder() {
        return List.of(
                new HlsRenditionProfile("360p", 640, 360, 800),
                new HlsRenditionProfile("720p", 1280, 720, 2_800),
                new HlsRenditionProfile("1080p", 1920, 1080, 5_000)
        );
    }
}
