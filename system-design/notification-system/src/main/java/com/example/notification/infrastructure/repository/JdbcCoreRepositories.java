package com.example.notification.infrastructure.repository;

import com.example.notification.application.Exceptions;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Produkcyjna granica trwałości uruchamiana profilem postgres-runtime.
 * Constrainty są mechanizmem koordynacji; mapy JVM nie uczestniczą w tej ścieżce.
 */
public final class JdbcCoreRepositories {
    private JdbcCoreRepositories() { }
    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private static String channels(List<Channel> values) { return values.stream().map(Enum::name).reduce((a,b) -> a + "," + b).orElse(""); }
    private static List<Channel> channels(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).map(Channel::valueOf).toList();
    }
    private static String json(ObjectMapper mapper, Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static Map<String,Object> payload(ObjectMapper mapper, String value) {
        try { return value == null ? Map.of() : mapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    @Repository @Profile("postgres-runtime")
    public static class Notifications implements Ports.NotificationRepository {
        private final JdbcTemplate jdbc; private final ObjectMapper mapper;
        public Notifications(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

        @Override public InsertResult insertIfAbsent(Notification n) {
            try {
                jdbc.update("""
                    INSERT INTO notification_requests(notification_id,tenant_id,idempotency_key,request_fingerprint,status,
                    scheduled_at,created_at,user_id,notification_type,requested_channels,selected_channels,contact_email,
                    contact_phone,contact_push,payload,deduplication_key,expires_at,updated_at,archived_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, n.getId(), n.getTenantId(), n.getIdempotencyKey(), n.getRequestFingerprint(), n.getStatus().name(),
                        ts(n.getScheduledAt()), ts(n.getCreatedAt()), n.getUserId(), n.getNotificationType().name(),
                        channels(n.getRequestedChannels()), channels(n.getSelectedChannels()), n.getContactPoint().email(),
                        n.getContactPoint().phoneNumber(), n.getContactPoint().pushToken(), json(mapper,n.getPayload()),
                        n.getDeduplicationKey(), ts(n.getExpiresAt()), ts(n.getUpdatedAt()), ts(n.getArchivedAt()));
                return new InsertResult(n, true, null);
            } catch (DuplicateKeyException duplicate) {
                Notification existing = findByIdempotencyKey(n.getTenantId(), n.getIdempotencyKey()).orElseThrow(() -> duplicate);
                if (!Objects.equals(existing.getRequestFingerprint(), n.getRequestFingerprint()))
                    throw new Exceptions.IdempotencyConflictException("Idempotency key was already used with a different request");
                return new InsertResult(existing, false, "IDEMPOTENCY_KEY_MATCH");
            }
        }
        @Override public Notification save(Notification n) {
            jdbc.update("UPDATE notification_requests SET status=?,user_id=?,contact_email=?,contact_phone=?,contact_push=?,payload=?,updated_at=?,archived_at=? WHERE notification_id=?",
                    n.getStatus().name(),n.getUserId(),n.getContactPoint().email(),n.getContactPoint().phoneNumber(),n.getContactPoint().pushToken(),
                    json(mapper,n.getPayload()),ts(n.getUpdatedAt()),ts(n.getArchivedAt()),n.getId()); return n;
        }
        private Notification map(ResultSet rs) throws SQLException {
            return Notification.restore(rs.getObject("notification_id",UUID.class),rs.getString("tenant_id"),rs.getString("user_id"),
                    NotificationType.valueOf(rs.getString("notification_type")),channels(rs.getString("requested_channels")),
                    channels(rs.getString("selected_channels")),new ContactPoint(rs.getString("contact_email"),rs.getString("contact_phone"),rs.getString("contact_push")),
                    payload(mapper,rs.getString("payload")),rs.getString("idempotency_key"),rs.getString("deduplication_key"),rs.getString("request_fingerprint"),
                    NotificationStatus.valueOf(rs.getString("status")),instant(rs,"expires_at"),instant(rs,"scheduled_at"),
                    instant(rs,"created_at"),instant(rs,"updated_at"),instant(rs,"archived_at"));
        }
        private Optional<Notification> one(String sql,Object... args) { return jdbc.query(sql,(rs,n)->map(rs),args).stream().findFirst(); }
        @Override public Optional<Notification> findById(String tenant,UUID id){return one("SELECT * FROM notification_requests WHERE tenant_id=? AND notification_id=?",tenant,id);}
        @Override public List<Notification> findAll(String tenant){return jdbc.query("SELECT * FROM notification_requests WHERE tenant_id=? ORDER BY created_at DESC",(rs,n)->map(rs),tenant);}
        @Override public List<Notification> findTerminalOlderThan(Instant t){return jdbc.query("SELECT * FROM notification_requests WHERE status IN ('DELIVERED','BOUNCED','FAILED','EXPIRED','CANCELLED','PARTIALLY_DELIVERED') AND updated_at<?",(rs,n)->map(rs),ts(t));}
        @Override public Optional<Notification> findByIdempotencyKey(String t,String k){if(k==null)return Optional.empty();return one("SELECT * FROM notification_requests WHERE tenant_id=? AND idempotency_key=?",t,k);}
        @Override public Optional<Notification> findByDeduplicationKey(String t,String k){if(k==null)return Optional.empty();return one("SELECT * FROM notification_requests WHERE tenant_id=? AND deduplication_key=? ORDER BY created_at DESC LIMIT 1",t,k);}
        @Override public int anonymizeUser(String tenant,String user){return jdbc.update("UPDATE notification_requests SET user_id=?,contact_email=NULL,contact_phone=NULL,contact_push=NULL,payload='{}',updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND user_id=?","erased:"+UUID.nameUUIDFromBytes((tenant+":"+user).getBytes()),tenant,user);}
    }

    @Repository @Profile("postgres-runtime")
    public static class Outbox implements Ports.OutboxRepository {
        private final JdbcTemplate jdbc; public Outbox(JdbcTemplate jdbc){this.jdbc=jdbc;}
        @Override public OutboxEvent save(OutboxEvent e){
            int updated=jdbc.update("UPDATE notification_outbox SET status=?,attempts=?,last_error=?,next_attempt_at=?,published_at=? WHERE event_id=?",e.getStatus().name(),e.getAttempts(),e.getLastError(),ts(e.getNextAttemptAt()),ts(e.getPublishedAt()),e.getId());
            if(updated==0)jdbc.update("INSERT INTO notification_outbox(event_id,tenant_id,aggregate_id,event_type,status,attempts,next_attempt_at,created_at,last_error,published_at) VALUES(?,?,?,?,?,?,?,?,?,?)",e.getId(),e.getTenantId(),e.getAggregateId(),e.getEventType(),e.getStatus().name(),e.getAttempts(),ts(e.getNextAttemptAt()),ts(e.getCreatedAt()),e.getLastError(),ts(e.getPublishedAt()));return e;}
        private OutboxEvent map(ResultSet rs)throws SQLException{return OutboxEvent.restore(rs.getObject("event_id",UUID.class),rs.getString("tenant_id"),rs.getObject("aggregate_id",UUID.class),rs.getString("event_type"),OutboxStatus.valueOf(rs.getString("status")),rs.getString("last_error"),instant(rs,"created_at"),instant(rs,"published_at"),instant(rs,"next_attempt_at"),rs.getInt("attempts"));}
        @Override public List<OutboxEvent> findPending(int limit){return jdbc.query("SELECT * FROM notification_outbox WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED",(rs,n)->map(rs),limit);}
        @Override public List<OutboxEvent> findAll(String tenant){return jdbc.query("SELECT * FROM notification_outbox WHERE tenant_id=? ORDER BY created_at DESC",(rs,n)->map(rs),tenant);}
        @Override public long countPending(){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM notification_outbox WHERE status='PENDING'",Long.class);return n==null?0:n;}
        @Override public Optional<Instant> oldestPendingAt(){return jdbc.query("SELECT MIN(created_at) AS oldest FROM notification_outbox WHERE status='PENDING'",(rs,n)->instant(rs,"oldest")).stream().filter(Objects::nonNull).findFirst();}
    }

    @Repository @Profile("postgres-runtime")
    public static class Jobs implements Ports.NotificationJobRepository {
        private final JdbcTemplate jdbc; private final ObjectMapper mapper;
        public Jobs(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
        @Override public NotificationJob save(NotificationJob j){int u=jdbc.update("UPDATE notification_jobs SET status=?,attempt_count=?,next_attempt_at=?,provider_name=?,provider_message_id=?,last_error=?,fallback_used=?,user_id=?,contact_email=?,contact_phone=?,contact_push=?,payload=?,updated_at=? WHERE job_id=?",j.getStatus().name(),j.getAttemptCount(),ts(j.getNextAttemptAt()),j.getProviderName(),j.getProviderMessageId(),j.getLastError(),j.isFallbackUsed(),j.getUserId(),j.getContactPoint().email(),j.getContactPoint().phoneNumber(),j.getContactPoint().pushToken(),json(mapper,j.getPayload()),ts(j.getUpdatedAt()),j.getId());if(u==0)insert(j);return j;}
        private void insert(NotificationJob j){jdbc.update("INSERT INTO notification_jobs(job_id,notification_id,tenant_id,channel,priority,template_id,template_version,status,attempt_count,next_attempt_at,user_id,notification_type,contact_email,contact_phone,contact_push,payload,max_attempts,provider_name,provider_message_id,last_error,fallback_used,expires_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",j.getId(),j.getNotificationId(),j.getTenantId(),j.getChannel().name(),j.getPriority().name(),j.getTemplateId(),j.getTemplateVersion(),j.getStatus().name(),j.getAttemptCount(),ts(j.getNextAttemptAt()),j.getUserId(),j.getNotificationType().name(),j.getContactPoint().email(),j.getContactPoint().phoneNumber(),j.getContactPoint().pushToken(),json(mapper,j.getPayload()),j.getMaxAttempts(),j.getProviderName(),j.getProviderMessageId(),j.getLastError(),j.isFallbackUsed(),ts(j.getExpiresAt()),ts(j.getCreatedAt()),ts(j.getUpdatedAt()));}
        @Override public NotificationJob saveIfAbsent(NotificationJob j){try{insert(j);return j;}catch(DuplicateKeyException e){return jdbc.query("SELECT * FROM notification_jobs WHERE notification_id=? AND channel=?",(rs,n)->map(rs),j.getNotificationId(),j.getChannel().name()).getFirst();}}
        private NotificationJob map(ResultSet rs)throws SQLException{return NotificationJob.restore(rs.getObject("job_id",UUID.class),rs.getObject("notification_id",UUID.class),rs.getString("tenant_id"),rs.getString("user_id"),NotificationType.valueOf(rs.getString("notification_type")),Channel.valueOf(rs.getString("channel")),new ContactPoint(rs.getString("contact_email"),rs.getString("contact_phone"),rs.getString("contact_push")),payload(mapper,rs.getString("payload")),rs.getObject("template_id",UUID.class),rs.getInt("template_version"),NotificationStatus.valueOf(rs.getString("status")),rs.getInt("attempt_count"),rs.getInt("max_attempts"),instant(rs,"next_attempt_at"),rs.getString("provider_name"),rs.getString("provider_message_id"),rs.getString("last_error"),rs.getBoolean("fallback_used"),instant(rs,"expires_at"),instant(rs,"created_at"),instant(rs,"updated_at"));}
        private List<NotificationJob> list(String sql,Object...a){return jdbc.query(sql,(rs,n)->map(rs),a);}
        @Override public Optional<NotificationJob> findById(UUID id){return list("SELECT * FROM notification_jobs WHERE job_id=?",id).stream().findFirst();}
        @Override public Optional<NotificationJob> findByProviderMessageId(String id){return list("SELECT * FROM notification_jobs WHERE provider_message_id=?",id).stream().findFirst();}
        @Override public List<NotificationJob> findByNotificationId(UUID id){return list("SELECT * FROM notification_jobs WHERE notification_id=? ORDER BY created_at",id);}
        @Override public List<NotificationJob> findByNotificationId(String t,UUID id){return list("SELECT * FROM notification_jobs WHERE tenant_id=? AND notification_id=? ORDER BY created_at",t,id);}
        @Override public List<NotificationJob> findAll(String t){return list("SELECT * FROM notification_jobs WHERE tenant_id=? ORDER BY created_at DESC",t);}
        @Override public List<NotificationJob> findDeadLetterJobs(String t){return list("SELECT * FROM notification_jobs WHERE tenant_id=? AND dead_letter=TRUE",t);}
        @Override public List<NotificationJob> findReconcilableOlderThan(Instant t){return list("SELECT * FROM notification_jobs WHERE status IN ('UNKNOWN','SENT') AND updated_at<?",ts(t));}
        @Override public void moveToDeadLetter(NotificationJob j){jdbc.update("UPDATE notification_jobs SET dead_letter=TRUE WHERE job_id=?",j.getId());}
        @Override public int anonymizeUser(String t,String u){return jdbc.update("UPDATE notification_jobs SET user_id=?,contact_email=NULL,contact_phone=NULL,contact_push=NULL,payload='{}',updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND user_id=?","erased:"+UUID.nameUUIDFromBytes((t+":"+u).getBytes()),t,u);}
        @Override public long countDeadLetter(){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM notification_jobs WHERE dead_letter=TRUE",Long.class);return n==null?0:n;}
        @Override public Optional<Instant> oldestReadyAt(Instant now){return jdbc.query("SELECT MIN(next_attempt_at) AS oldest FROM notification_jobs WHERE status='QUEUED' AND next_attempt_at<=?",(rs,n)->instant(rs,"oldest"),ts(now)).stream().filter(Objects::nonNull).findFirst();}
    }

    @Repository @Profile("postgres-runtime")
    public static class Attempts implements Ports.DeliveryAttemptRepository {
        private final JdbcTemplate jdbc; public Attempts(JdbcTemplate jdbc){this.jdbc=jdbc;}
        @Override public DeliveryAttempt save(DeliveryAttempt a){jdbc.update("INSERT INTO delivery_attempts(attempt_id,job_id,attempt_number,provider_name,outcome,provider_message_id,error_message,occurred_at) VALUES(?,?,?,?,?,?,?,?)",a.id(),a.jobId(),a.number(),a.providerName(),a.outcome().name(),a.providerMessageId(),a.error(),ts(a.occurredAt()));return a;}
        @Override public List<DeliveryAttempt> findByJobId(UUID id){return jdbc.query("SELECT * FROM delivery_attempts WHERE job_id=? ORDER BY attempt_number",(rs,n)->new DeliveryAttempt(rs.getObject("attempt_id",UUID.class),rs.getObject("job_id",UUID.class),rs.getInt("attempt_number"),rs.getString("provider_name"),DeliveryAttempt.Outcome.valueOf(rs.getString("outcome")),rs.getString("provider_message_id"),rs.getString("error_message"),instant(rs,"occurred_at")),id);}
    }

    @Repository @Profile("postgres-runtime")
    public static class Inbox implements Ports.WebhookInboxRepository {
        private final JdbcTemplate jdbc; public Inbox(JdbcTemplate jdbc){this.jdbc=jdbc;}
        @Override public WebhookInboxEvent saveIfAbsent(WebhookInboxEvent e){try{jdbc.update("INSERT INTO webhook_inbox(event_id,provider_message_id,status,reason,received_at) VALUES(?,?,?,?,?)",e.getEventId(),e.getProviderMessageId(),e.getStatus().name(),e.getReason(),ts(e.getReceivedAt()));return e;}catch(DuplicateKeyException x){return find(e.getEventId()).orElseThrow();}}
        @Override public WebhookInboxEvent save(WebhookInboxEvent e){jdbc.update("UPDATE webhook_inbox SET processed_at=?,last_error=?,attempts=?,next_attempt_at=?,dead=? WHERE event_id=?",ts(e.getProcessedAt()),e.getLastError(),e.getAttempts(),ts(e.getNextAttemptAt()),e.isDead(),e.getEventId());return e;}
        private WebhookInboxEvent map(ResultSet rs)throws SQLException{WebhookInboxEvent e=new WebhookInboxEvent(rs.getString("event_id"),rs.getString("provider_message_id"),ProviderWebhookStatus.valueOf(rs.getString("status")),rs.getString("reason"),instant(rs,"received_at"));e.restoreProcessing(instant(rs,"processed_at"),rs.getString("last_error"),rs.getInt("attempts"),instant(rs,"next_attempt_at"),rs.getBoolean("dead"));return e;}
        private Optional<WebhookInboxEvent> find(String id){return jdbc.query("SELECT * FROM webhook_inbox WHERE event_id=?",(rs,n)->map(rs),id).stream().findFirst();}
        @Override public List<WebhookInboxEvent> findPending(){return jdbc.query("SELECT * FROM webhook_inbox WHERE processed_at IS NULL AND dead=FALSE AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY received_at",(rs,n)->map(rs));}
    }

    @Repository @Profile("postgres-runtime")
    public static class Suppressions implements Ports.SuppressionRepository {
        private final JdbcTemplate jdbc; public Suppressions(JdbcTemplate jdbc){this.jdbc=jdbc;}
        @Override public SuppressionEntry save(SuppressionEntry e){jdbc.update("INSERT INTO suppression_entries(tenant_id,channel,contact_hash,reason,created_at) VALUES(?,?,?,?,?) ON CONFLICT (tenant_id,channel,contact_hash) DO UPDATE SET reason=EXCLUDED.reason",e.tenantId(),e.channel().name(),e.contactHash(),e.reason(),ts(e.createdAt()));return e;}
        @Override public boolean exists(String t,Channel c,String h){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM suppression_entries WHERE tenant_id=? AND channel=? AND contact_hash=?",Integer.class,t,c.name(),h);return n!=null&&n>0;}
        @Override public void delete(String t,Channel c,String h){jdbc.update("DELETE FROM suppression_entries WHERE tenant_id=? AND channel=? AND contact_hash=?",t,c.name(),h);}
        @Override public void deleteForTenant(String t){jdbc.update("DELETE FROM suppression_entries WHERE tenant_id=?",t);}
    }
}
