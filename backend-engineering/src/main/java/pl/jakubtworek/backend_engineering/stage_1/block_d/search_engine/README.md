# Silnik wyszukiwania jako odtwarzalny read model

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** Silnik wyszukiwania jako odtwarzalny read model.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „Silnik wyszukiwania jako odtwarzalny read model” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=OpenSearchContainerTest,VersionedSearchIndexTest" test`
> - **Role klas:** `NaiveSearchProjection` = `naive`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Problem

Full-text search, ranking i facety wymagają innego modelu od relacyjnego źródła
prawdy. Zdarzenia zasilające indeks mogą jednak dotrzeć ponownie, w złej
kolejności albo po usunięciu dokumentu.

## Niezmiennik

Stan indeksu odpowiada najwyższej zastosowanej wersji rekordu źródłowego. Stare
zdarzenie nie nadpisuje nowego, a tombstone nie pozwala spóźnionej aktualizacji
wskrzesić usuniętego dokumentu.

## Model i kontrprzykład

`NaiveSearchProjection` stosuje kolejność dostarczenia, więc starsze zdarzenie
może wygrać. `VersionedSearchIndex` przechowuje watermark per dokument, usuwa
stare termy z inverted index i stosuje tombstones. Wyniki są porządkowane przez
stabilną parę `(score desc, id asc)`, którą wykorzystuje `SearchCursor`.
`SearchPointInTime` zamraża logiczny snapshot, dzięki czemu zmiany indeksu między
stronami nie przesuwają ani nie dublują wyników.

Model pokazuje exact-token search, prosty ranking oraz `search_after`. Nie
udaje pełnego analyzera OpenSearch/Elasticsearch.

## Najważniejszy test

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress -Dtest=VersionedSearchIndexTest test
```

Test dowodzi ochrony przed zmianą kolejności, semantyki tombstone i stabilnej
paginacji z PIT oraz reprodukuje błąd naiwnej projekcji. `OpenSearchContainerTest`
w profilu `infrastructure-tests` potwierdza mapping, analyzer i external version
na prawdziwym OpenSearch.

## Kiedy użyć, a kiedy nie

Osobny silnik ma sens dla full-text, relevance, facetingu i niezależnego
skalowania read modelu. Nie powinien zastępować SQL dla transakcji, silnych
constraintów ani zapisu stanu biznesowego. Prostsze wyszukiwanie może pozostać w
PostgreSQL, jeśli jego funkcje indeksowania spełniają wymagania.

## Granice produkcyjne

Test kontenerowy pokrywa pojedynczy węzeł. Produkcja nadal wymaga shardów,
refresh policy, bulk API, alias-based reindex, CDC/outbox, backfillu, drift
detection i rekoncyliacji. Indeks musi dać się odbudować ze źródła prawdy bez
ponownego wykonania efektów biznesowych.
