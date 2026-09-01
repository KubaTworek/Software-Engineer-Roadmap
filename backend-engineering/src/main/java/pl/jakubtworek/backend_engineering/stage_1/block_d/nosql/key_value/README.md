# key value

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** key value.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „key value” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=AtomicFixedWindowRateLimiterTest" test`
> - **Role klas:** `AtomicFixedWindowRateLimiter` = `correct`.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Key-value: klucz, atomowość i TTL



Key-value store jest dobrym wyborem, gdy aplikacja zna pełny klucz i potrzebuje
krótkiej, przewidywalnej operacji. `SessionCacheEntry` pokazuje wartość sesji, a
`RateLimitEntry` jej niemutowalny model. Żaden z nich sam nie zapewnia atomowości
rozproszonego read-modify-write.

`AtomicFixedWindowRateLimiter` jest wykonywalnym modelem jednej operacji
serwerowej:

1. odczytaj bieżące okno,
2. jeśli TTL wygasł, utwórz nowe okno,
3. sprawdź limit i zwiększ licznik,
4. zachowaj pierwotny `resetAt`,
5. zwróć `allowed`, `remaining` i `retryAfter`.

W modelu atomowość zapewnia `synchronized`. W Redisie wszystkie kroki powinien
wykonać jeden skrypt Lua albo funkcja serwerowa. Sekwencja osobnych `GET`, `SET`
i `EXPIRE` może zgubić inkrementację albo pozostawić licznik bez czasu
wygaśnięcia.

`RedisFixedWindowScripts.INCREMENT_AND_SET_TTL` zawiera rzeczywistą operację Lua:
`INCR` tworzy lub zwiększa licznik, a pierwsza inkrementacja ustawia `PEXPIRE`.
Cały skrypt jest atomowy z perspektywy innych komend Redisa. Test
`RedisAtomicCounterContainerTest` uruchamia go współbieżnie na prawdziwym Redisie
i sprawdza zarówno końcową wartość, jak i TTL. Model in-memory nadal służy do
szybkiego testowania reguł limitu, natomiast test kontenerowy weryfikuje gwarancję
konkretnego silnika.

Odrzucone żądanie nie przesuwa TTL. Dokładnie w chwili `resetAt` zaczyna się nowe
okno. Test równoległy wysyła sto operacji i potwierdza, że przy limicie dziesięć
zaakceptowanych zostaje dokładnie dziesięć.

Fixed window jest prosty, ale pozwala na burst na granicy dwóch okien. Gdy to
łamie wymagania, rozważ sliding log, sliding window counter lub token bucket i
jawnie policz ich koszt pamięci oraz dokładność.
