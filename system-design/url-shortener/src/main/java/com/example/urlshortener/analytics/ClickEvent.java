package com.example.urlshortener.analytics;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "click_events", indexes = {
    @Index(name = "idx_click_events_short_code_clicked_at", columnList = "short_code, clicked_at"),
    @Index(name = "idx_click_events_clicked_at", columnList = "clicked_at"),
    @Index(name = "idx_click_events_country", columnList = "country"),
    @Index(name = "idx_click_events_device_type", columnList = "device_type"),
    @Index(name = "idx_click_events_referrer_domain", columnList = "referrer_domain"),
    @Index(name = "idx_click_events_suspicious", columnList = "suspicious")
})
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "click_event_id_seq")
    @SequenceGenerator(name = "click_event_id_seq", sequenceName = "click_event_id_seq", allocationSize = 100)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

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

    @Column(name = "referrer_domain", length = 255)
    private String referrerDomain;

    @Column(name = "country", length = 8)
    private String country;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(name = "browser", length = 32)
    private String browser;

    @Column(name = "suspicious", nullable = false)
    private boolean suspicious;

    @Column(name = "abuse_reason", columnDefinition = "TEXT")
    private String abuseReason;

    protected ClickEvent() {}

    public ClickEvent(
        String eventId,
        String shortCode,
        Instant clickedAt,
        String ipHash,
        String userAgent,
        String referrer,
        String referrerDomain,
        String country,
        String deviceType,
        String browser,
        boolean suspicious,
        String abuseReason
    ) {
        this.eventId = eventId;
        this.shortCode = shortCode;
        this.clickedAt = clickedAt;
        this.ipHash = ipHash;
        this.userAgent = userAgent;
        this.referrer = referrer;
        this.referrerDomain = referrerDomain;
        this.country = country;
        this.deviceType = deviceType;
        this.browser = browser;
        this.suspicious = suspicious;
        this.abuseReason = abuseReason;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getShortCode() { return shortCode; }
    public Instant getClickedAt() { return clickedAt; }
    public String getIpHash() { return ipHash; }
    public String getUserAgent() { return userAgent; }
    public String getReferrer() { return referrer; }
    public String getReferrerDomain() { return referrerDomain; }
    public String getCountry() { return country; }
    public String getDeviceType() { return deviceType; }
    public String getBrowser() { return browser; }
    public boolean isSuspicious() { return suspicious; }
    public String getAbuseReason() { return abuseReason; }
}
