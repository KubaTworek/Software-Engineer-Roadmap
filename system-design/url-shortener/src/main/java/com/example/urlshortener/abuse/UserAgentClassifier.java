package com.example.urlshortener.abuse;

import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class UserAgentClassifier {

    public String deviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "unknown";
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) return "bot";
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone")) return "mobile";
        if (ua.contains("ipad") || ua.contains("tablet")) return "tablet";
        return "desktop";
    }

    public String browser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return "unknown";
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("edg/")) return "edge";
        if (ua.contains("chrome/") && !ua.contains("chromium")) return "chrome";
        if (ua.contains("firefox/")) return "firefox";
        if (ua.contains("safari/") && !ua.contains("chrome/")) return "safari";
        if (ua.contains("curl/")) return "curl";
        if (ua.contains("bot")) return "bot";
        return "other";
    }
}
