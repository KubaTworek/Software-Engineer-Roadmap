# ADR 0012 — trwały import i ograniczony ownership Temporal

## Kontekst

Feed katalogu wymaga restartu porcji, a płatność UNKNOWN może czekać na wyjaśnienie
poza requestem HTTP. Istniejąca saga w PostgreSQL już posiada proces zamówienia.

## Decyzja

Import zapisuje staging i checkpoint w jednej transakcji Catalog; błędny feed
nie zmienia aktywnych cen. Opcjonalny Temporal worker wykonuje jedynie lookup
UNKNOWN przez istniejący use case. Nie przejmujemy sagi ani Kafka consumers.

## Alternatywy i konsekwencje

- Scheduler + DB wystarczy dla prostego pollingu; pozostaje prostszym wariantem.
- Temporal dodaje historię, durable timer i koszt infrastruktury oraz wersjonowania.
- Przeniesienie całej sagi zwiększyłoby ryzyko dwóch koordynatorów; wymaga osobnej migracji.
- Spring Batch jest w izolowanym laboratorium; projekt używa własnej transakcji JDBC
  bez dodawania niezgodnego stosu Spring do działającej aplikacji.
- Shadow migration dotyczy odczytu. Nie obiecuje bezpiecznego rollbacku nowych zapisów.

Dowody: testy PaymentReconciliationWorkflowTest, SupplierCatalogImportIntegrationTest
oraz ShadowCatalogReaderTest. Wdrożenie produkcyjne wymaga osobnego review auth,
retencji historii, reprezentatywności shadow sample i obserwowalności workera.
Uzupełnienie 2026-10-25: V7 dodaje lifecycle, kwarantannę i atomową publikację
snapshotu przez wskaźnik. Rozstrzygnięcie UNKNOWN używa CAS statusu i outboxa
w jednej transakcji; przegrany nie publikuje drugiego faktu. Równoległy lookup
może wystąpić, ale nie drugi reserve. Shadow ma ograniczony bulkhead, deadline
i okres ważności dowodów. Te mechanizmy nie przenoszą ownershipu zapisu.
