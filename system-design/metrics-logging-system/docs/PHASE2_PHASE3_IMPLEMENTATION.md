# Phase 2 and Phase 3 Implementation Notes

## Phase 2 checklist

| Capability | Status | Main classes |
|---|---|---|
| Object storage for cold data | Implemented with local object-store abstraction | `ObjectStorageService`, `ColdExportJob` |
| Downsampling metrics | Implemented with scheduled rollups | `MetricsDownsampler`, ClickHouse rollup tables |
| Bloom filters for logs | Implemented per tenant/service/level/hour | `SimpleBloomFilter`, `LogBloomFilterService` |
| Query planner | Implemented | `QueryPlanner`, `QueryPlan` |
| RBAC | Implemented with API keys | `ApiKeyAuthInterceptor`, `Rbac`, `AuthContext` |
| Quotas | Implemented as in-memory sliding minute counters | `QuotaService`, `QuotaProperties` |
| Alert routing | Implemented for log and webhook channels | `AlertRouter`, `NotificationChannel` |

## Phase 3 scaffold checklist

| Capability | Status | Main classes |
|---|---|---|
| Multi-region replication | Control-plane status endpoint only | `RegionReplicationService` |
| Anomaly detection | Basic z-score detector | `AnomalyDetector` |
| Metrics/log correlation | Implemented for metric timestamp + nearby error logs | `CorrelationService` |

## Design decisions

The implementation deliberately keeps Phase 2 runnable with `docker compose up --build`. For that reason, cold object storage uses local disk behind an object-storage interface rather than requiring AWS or MinIO credentials. Swapping it for S3 or MinIO should be contained inside `ObjectStorageService`.

Alert rules are still held in memory from the Phase 1 implementation. That is acceptable for a demo but should be replaced with PostgreSQL or another metadata store before serious use.

RBAC is API-key based rather than OAuth/OIDC. It is intentionally simple and explicit for local testing.

## Known production gaps

- Cold query execution from object storage is not fully implemented; the planner exposes when a query would use the cold tier.
- Downsampling is simple and should be made idempotent/deduplicated before production.
- Quotas are in-memory and should move to Redis or another distributed counter for multi-replica deployments.
- Bloom filters are per-hour and approximate; production systems should compact/merge them more carefully.
- API keys should be stored hashed in metadata DB, not plain in YAML/environment.
