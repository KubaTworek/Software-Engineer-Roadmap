package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Educational repository whose key always includes tenant id. In PostgreSQL the
 * equivalent is a composite key plus tenant predicate (and often RLS as defence in depth).
 */
public final class TenantDataRepository {

    private final Map<Key, TenantDataRecord> records = new ConcurrentHashMap<>();

    public void save(TenantDataRecord record) {
        records.put(new Key(record.tenantId(), record.subjectId()), record);
    }

    public Optional<TenantDataRecord> find(TenantId tenantId, String subjectId) {
        return Optional.ofNullable(records.get(new Key(tenantId, subjectId)));
    }

    public void anonymize(TenantId tenantId, String subjectId) {
        records.computeIfPresent(new Key(tenantId, subjectId), (ignored, record) -> record.anonymized());
    }

    private record Key(TenantId tenantId, String subjectId) {
    }
}
