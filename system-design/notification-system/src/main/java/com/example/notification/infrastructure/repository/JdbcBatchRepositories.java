package com.example.notification.infrastructure.repository;

import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Trwałe checkpointy pracy wsadowej: kampanii i digestów. */
public final class JdbcBatchRepositories {
    private JdbcBatchRepositories() { }
    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static String json(ObjectMapper mapper, Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static <T> T json(ObjectMapper mapper, String value, TypeReference<T> type) {
        try { return mapper.readValue(value, type); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Repository
    @Profile("postgres-runtime")
    public static class Campaigns implements Ports.CampaignRepository {
        private final JdbcTemplate jdbc;
        private final ObjectMapper mapper;
        public Campaigns(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

        @Override public Campaign save(Campaign c) {
            int updated = jdbc.update("""
                    UPDATE notification_campaigns SET status=?,next_recipient_index=?,failed_recipients=?,updated_at=?
                    WHERE campaign_id=? AND tenant_id=?
                    """, c.getStatus().name(), c.getNextRecipientIndex(), c.getFailedRecipients(),
                    ts(c.getUpdatedAt()), c.getId(), c.getTenantId());
            if (updated == 0) jdbc.update("""
                    INSERT INTO notification_campaigns(campaign_id,tenant_id,name,notification_type,user_ids,channels,
                    payload,status,next_recipient_index,failed_recipients,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """, c.getId(), c.getTenantId(), c.getName(), c.getNotificationType().name(),
                    json(mapper, c.getUserIds()), json(mapper, c.getChannels()), json(mapper, c.getPayload()),
                    c.getStatus().name(), c.getNextRecipientIndex(), c.getFailedRecipients(),
                    ts(c.getCreatedAt()), ts(c.getUpdatedAt()));
            return c;
        }
        private Campaign map(ResultSet rs) throws SQLException {
            List<String> users = json(mapper, rs.getString("user_ids"), new TypeReference<>() { });
            List<Channel> channels = json(mapper, rs.getString("channels"), new TypeReference<>() { });
            Map<String,Object> payload = json(mapper, rs.getString("payload"), new TypeReference<>() { });
            return Campaign.restore(rs.getObject("campaign_id", UUID.class), rs.getString("tenant_id"),
                    rs.getString("name"), NotificationType.valueOf(rs.getString("notification_type")), users,
                    channels, payload, CampaignStatus.valueOf(rs.getString("status")), instant(rs,"created_at"),
                    instant(rs,"updated_at"), rs.getInt("next_recipient_index"), rs.getInt("failed_recipients"));
        }
        @Override public Optional<Campaign> findById(String tenantId, UUID id) {
            return jdbc.query("SELECT * FROM notification_campaigns WHERE tenant_id=? AND campaign_id=?",
                    (rs,n)->map(rs), tenantId, id).stream().findFirst();
        }
        @Override public List<Campaign> findAll(String tenantId) {
            return jdbc.query("SELECT * FROM notification_campaigns WHERE tenant_id=? ORDER BY created_at DESC",
                    (rs,n)->map(rs), tenantId);
        }
        @Override @Transactional public Optional<Ports.CampaignRepository.Claim> claim(
                String tenantId, UUID id, Instant now, Instant leaseUntil) {
            List<Campaign> candidates = jdbc.query("""
                    SELECT * FROM notification_campaigns
                    WHERE tenant_id=? AND campaign_id=? AND next_recipient_index < json_array_length(user_ids::json)
                      AND (claim_until IS NULL OR claim_until < ?)
                    FOR UPDATE SKIP LOCKED
                    """, (rs,n)->map(rs), tenantId, id, ts(now));
            if (candidates.isEmpty()) return Optional.empty();
            UUID token = UUID.randomUUID();
            int updated = jdbc.update("""
                    UPDATE notification_campaigns SET claim_token=?, claim_until=?
                    WHERE tenant_id=? AND campaign_id=? AND (claim_until IS NULL OR claim_until < ?)
                    """, token, ts(leaseUntil), tenantId, id, ts(now));
            return updated == 1 ? Optional.of(new Ports.CampaignRepository.Claim(candidates.getFirst(), token))
                    : Optional.empty();
        }
        @Override public boolean checkpoint(Ports.CampaignRepository.Claim claim) {
            Campaign c = claim.campaign();
            return jdbc.update("""
                    UPDATE notification_campaigns SET status=?,next_recipient_index=?,failed_recipients=?,updated_at=?
                    WHERE campaign_id=? AND tenant_id=? AND claim_token=?
                    """, c.getStatus().name(), c.getNextRecipientIndex(), c.getFailedRecipients(),
                    ts(c.getUpdatedAt()), c.getId(), c.getTenantId(), claim.token()) == 1;
        }
        @Override public void release(Ports.CampaignRepository.Claim claim) {
            jdbc.update("UPDATE notification_campaigns SET claim_token=NULL,claim_until=NULL WHERE campaign_id=? AND claim_token=?",
                    claim.campaign().getId(), claim.token());
        }
    }

    @Repository
    @Profile("postgres-runtime")
    public static class Digests implements Ports.DigestRepository {
        private final JdbcTemplate jdbc;
        private final ObjectMapper mapper;
        private final Clock clock;
        public Digests(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
            this.jdbc = jdbc; this.mapper = mapper; this.clock = clock;
        }
        @Override public DigestBuffer save(DigestBuffer b) {
            Instant lease = b.isClaimed() ? clock.instant().plusSeconds(30) : null;
            int updated = jdbc.update("UPDATE notification_digest_buffers SET items=?,flushed=?,claimed=?,claim_until=? WHERE digest_id=?",
                    json(mapper,b.getItems()),b.isFlushed(),b.isClaimed(),ts(lease),b.getId());
            if (updated == 0) jdbc.update("""
                    INSERT INTO notification_digest_buffers(digest_id,tenant_id,user_id,digest_key,items,flush_at,flushed,claimed,claim_until)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """,b.getId(),b.getTenantId(),b.getUserId(),b.getDigestKey(),json(mapper,b.getItems()),ts(b.getFlushAt()),
                    b.isFlushed(),b.isClaimed(),ts(lease));
            return b;
        }
        private DigestBuffer map(ResultSet rs) throws SQLException {
            List<Map<String,Object>> items=json(mapper,rs.getString("items"),new TypeReference<>() { });
            return DigestBuffer.restore(rs.getObject("digest_id",UUID.class),rs.getString("tenant_id"),rs.getString("user_id"),
                    rs.getString("digest_key"),instant(rs,"flush_at"),items,rs.getBoolean("flushed"),rs.getBoolean("claimed"));
        }
        @Override public Optional<DigestBuffer> findOpen(String tenant,String user,String key) {
            return jdbc.query("SELECT * FROM notification_digest_buffers WHERE tenant_id=? AND user_id=? AND digest_key=? AND flushed=FALSE ORDER BY flush_at LIMIT 1",
                    (rs,n)->map(rs),tenant,user,key).stream().findFirst();
        }
        @Override public List<DigestBuffer> findReadyToFlush(Instant now) {
            return jdbc.query("SELECT * FROM notification_digest_buffers WHERE flushed=FALSE AND flush_at<=? AND (claimed=FALSE OR claim_until<?)",
                    (rs,n)->map(rs),ts(now),ts(now));
        }
        @Override @Transactional public List<DigestBuffer> claimReadyToFlush(Instant now) {
            var buffers=jdbc.query("SELECT * FROM notification_digest_buffers WHERE flushed=FALSE AND flush_at<=? AND (claimed=FALSE OR claim_until<?) ORDER BY flush_at LIMIT 25 FOR UPDATE SKIP LOCKED",
                    (rs,n)->map(rs),ts(now),ts(now));
            buffers.forEach(buffer -> {
                buffer.tryClaim();
                jdbc.update("UPDATE notification_digest_buffers SET claimed=TRUE,claim_until=? WHERE digest_id=?",
                        ts(now.plusSeconds(30)),buffer.getId());
            });
            return buffers;
        }
    }
}
