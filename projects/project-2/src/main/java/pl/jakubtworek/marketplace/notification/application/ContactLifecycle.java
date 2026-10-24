package pl.jakubtworek.marketplace.notification.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Właścicielem PII kontaktu jest Notification. Zamówienie zachowuje referencję finansową, nie adres email. */
@Service @Profile("postgres")
public class ContactLifecycle {
    public record Contact(UUID customerId,String email,String displayName) {}
    public record Erasure(UUID customerId,UUID requestId,long sequence) {}
    public record Registry(long watermark,List<Erasure> entries) { public Registry { entries=List.copyOf(entries); } }
    public record RegistrySeal(long watermark,int entries,String sha256) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Map<UUID,Contact> cache=new LinkedHashMap<>(16,0.75f,true);
    public ContactLifecycle(JdbcTemplate jdbc,PlatformTransactionManager transactions){this.jdbc=jdbc;this.tx=new TransactionTemplate(transactions);}
    public void put(Contact contact) {
        tx.executeWithoutResult(status -> {
            lock(contact.customerId());
            if(erased(contact.customerId()))throw new IllegalStateException("Contact was erased; stale replay cannot recreate it");
            jdbc.update("insert into notification.contacts values (?,?,?) on conflict(customer_id) do update set email=excluded.email,display_name=excluded.display_name",
                    contact.customerId(),contact.email(),contact.displayName());
            jdbc.update("insert into notification.contact_projection values (?,?) on conflict(customer_id) do update set email=excluded.email",contact.customerId(),contact.email());
        });
        evictAfterCommit(contact.customerId());
    }
    public Optional<Contact> read(UUID customer) {
        // Tombstone jest sprawdzany przed cache. Awaria eviction nie może ujawnić starego PII po zakończeniu erase.
        if(erased(customer)){evict(customer);return Optional.empty();}
        boolean transactional=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive();
        synchronized(cache){if(!transactional&&cache.containsKey(customer))return Optional.of(cache.get(customer));}
        var found=jdbc.query("select email,display_name from notification.contacts where customer_id=?",
                (rs,n)->new Contact(customer,rs.getString(1),rs.getString(2)),customer).stream().findFirst();
        if(!transactional)found.ifPresent(contact -> {synchronized(cache){
            if(cache.size()>=1024)cache.remove(cache.keySet().iterator().next());cache.put(customer,contact);
        }});
        return found;
    }
    public UUID erase(UUID customer,UUID requestId) {
        UUID result=tx.execute(status -> {
            lock(customer);
            jdbc.update("insert into notification.erasure_registry(customer_id,request_id) values (?,?) on conflict(customer_id) do nothing",customer,requestId);
            UUID stable=jdbc.queryForObject("select request_id from notification.erasure_registry where customer_id=?",UUID.class,customer);
            jdbc.update("delete from notification.contacts where customer_id=?",customer);
            jdbc.update("delete from notification.contact_projection where customer_id=?",customer);
            jdbc.update("insert into notification.erasure_outbox(event_id,customer_id) values (?,?) on conflict(event_id) do nothing",stable,customer);
            return stable;
        });
        evictAfterCommit(customer); return result;
    }
    public Registry exportRegistry() {
        List<Erasure> entries=jdbc.query("select customer_id,request_id,sequence from notification.erasure_registry order by sequence",
                (rs,n)->new Erasure(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getLong(3)));
        long watermark=entries.stream().mapToLong(Erasure::sequence).max().orElse(0);
        return new Registry(watermark,entries);
    }
    /** Wyłącznie maintenance przy zamkniętym ruchu. Minimum pochodzi z zaufanego, osobno przechowanego manifestu. */
    public void suppressAfterRestore(Registry external,RegistrySeal trustedManifest) {
        long actual=external.entries().stream().mapToLong(Erasure::sequence).max().orElse(0);
        if(external.watermark()!=actual || !seal(external).equals(trustedManifest)
                || external.entries().stream().map(Erasure::customerId).distinct().count()!=external.entries().size()
                || external.entries().stream().map(Erasure::requestId).distinct().count()!=external.entries().size())
            throw new IllegalArgumentException("Incomplete or stale erasure registry");
        tx.executeWithoutResult(status -> {
            for(Erasure erasure:external.entries()) {
                lock(erasure.customerId());
                jdbc.update("insert into notification.erasure_registry(customer_id,request_id,sequence) values (?,?,?) on conflict(customer_id) do nothing",erasure.customerId(),erasure.requestId(),erasure.sequence());
                jdbc.update("delete from notification.contacts where customer_id=?",erasure.customerId());
                jdbc.update("delete from notification.contact_projection where customer_id=?",erasure.customerId());
                jdbc.update("insert into notification.erasure_outbox(event_id,customer_id) values (?,?) on conflict(event_id) do nothing",erasure.requestId(),erasure.customerId());
            }
            jdbc.execute("select setval(pg_get_serial_sequence('notification.erasure_registry','sequence'),coalesce(max(sequence),1),max(sequence) is not null) from notification.erasure_registry");
        });
        synchronized(cache){cache.clear();}
    }
    public static RegistrySeal seal(Registry registry) {
        String body=registry.entries().stream().sorted(Comparator.comparingLong(Erasure::sequence))
                .map(e->e.sequence()+":"+e.customerId()+":"+e.requestId()).collect(java.util.stream.Collectors.joining("\n"));
        try{return new RegistrySeal(registry.watermark(),registry.entries().size(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private void evict(UUID id){synchronized(cache){cache.remove(id);}}
    private void evictAfterCommit(UUID id) {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()){evict(id);return;}
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization(){public void afterCommit(){evict(id);}});
    }
    private boolean erased(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from notification.erasure_registry where customer_id=?)",Boolean.class,id));}
    private void lock(UUID id){jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
        try(var s=connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?,0))")){s.setString(1,"contact:"+id);s.execute();}return null;});}
}
