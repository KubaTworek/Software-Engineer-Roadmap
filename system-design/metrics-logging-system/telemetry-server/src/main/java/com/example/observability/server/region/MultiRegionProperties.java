package com.example.observability.server.region;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "telemetry.region")
public class MultiRegionProperties {
    private String current = "local";
    private String mode = "single-region";
    private List<String> peers = new ArrayList<>();
    private boolean replicationEnabled = false;
    private long maxHealthyLagMs = 300000;

    public String getCurrent() {
        return current;
    }

    public void setCurrent(String current) {
        this.current = current;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getPeers() {
        return peers;
    }

    public void setPeers(List<String> peers) {
        this.peers = peers;
    }

    public boolean isReplicationEnabled() {
        return replicationEnabled;
    }

    public void setReplicationEnabled(boolean replicationEnabled) {
        this.replicationEnabled = replicationEnabled;
    }

    public long getMaxHealthyLagMs() {
        return maxHealthyLagMs;
    }

    public void setMaxHealthyLagMs(long maxHealthyLagMs) {
        this.maxHealthyLagMs = maxHealthyLagMs;
    }
}
