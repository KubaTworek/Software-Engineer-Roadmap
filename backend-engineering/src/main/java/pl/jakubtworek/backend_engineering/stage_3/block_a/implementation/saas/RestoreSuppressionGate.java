package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.util.*;

/** Model bramy PRZED otwarciem ruchu; registry musi pochodzić spoza przywracanego backupu. */
public final class RestoreSuppressionGate {
    public static List<TenantDataRecord> allowed(List<TenantDataRecord> snapshot,
            List<DataLifecycleCoordinator.BackupErasureTombstone> currentRegistry) {
        record Subject(TenantId tenant,String id) {}
        Set<Subject> erased=new HashSet<>();
        currentRegistry.forEach(t->erased.add(new Subject(t.tenantId(),t.subjectReference())));
        // retainUntil jest minimalnym horyzontem utrzymania wpisu. Sam zegar nie dowodzi,
        // że wszystkie stare backupy i źródła replay przestały istnieć.
        return snapshot.stream().filter(r->!erased.contains(new Subject(r.tenantId(),r.subjectId()))).toList();
    }
    private RestoreSuppressionGate(){}
}
