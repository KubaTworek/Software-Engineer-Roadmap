package com.example.urlshortener.abuse;

public record AbuseVerdict(
    boolean suspicious,
    boolean shouldAutoBlock,
    String reason,
    String deviceType,
    String browser
) {
    public static AbuseVerdict clean(String deviceType, String browser) {
        return new AbuseVerdict(false, false, null, deviceType, browser);
    }

    public static AbuseVerdict suspicious(String reason, boolean shouldAutoBlock, String deviceType, String browser) {
        return new AbuseVerdict(true, shouldAutoBlock, reason, deviceType, browser);
    }
}
