package com.example.urlshortener.analytics;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "click_events", indexes = {
    @Index(name = "idx_click_events_short_code_clicked_at", columnList = "short_code, clicked_at"),
    @Index(name = "idx_click_events_clicked_at", columnList = "clicked_at")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "click_event_id_seq")
    @SequenceGenerator(name = "click_event_id_seq", sequenceName = "click_event_id_seq", allocationSize = 100)
    private Long id;

    @Column(name = "short_code", nullable = false, length = 32)
    private String shortCode;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "ip_hash", length = 128)
    private String ipHash;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "referrer", columnDefinition = "TEXT")
    private String referrer;

    protected ClickEvent() {}

    public ClickEvent(String shortCode, Instant clickedAt, String ipHash, String userAgent, String referrer) {
        this.shortCode = shortCode;
        this.clickedAt = clickedAt;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
        this.referrer = referrer;
    }

    public Long getId() { return id; }
    public String getShortCode() { return shortCode; }
    public Instant getClickedAt() { return clickedAt; }
    public String getIpHash() { return ipHash; }
    public String getUserAgent() { return userAgent; }
    public String getReferrer() { return referrer; }
}
