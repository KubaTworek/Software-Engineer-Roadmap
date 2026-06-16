package com.example.observability.server.fulltext;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "telemetry.fulltext")
public class FullTextIndexProperties {
    private boolean enabled = true;
    private int maxTermsPerLog = 64;
    private int minTermLength = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxTermsPerLog() {
        return maxTermsPerLog;
    }

    public void setMaxTermsPerLog(int maxTermsPerLog) {
        this.maxTermsPerLog = maxTermsPerLog;
    }

    public int getMinTermLength() {
        return minTermLength;
    }

    public void setMinTermLength(int minTermLength) {
        this.minTermLength = minTermLength;
    }
}
