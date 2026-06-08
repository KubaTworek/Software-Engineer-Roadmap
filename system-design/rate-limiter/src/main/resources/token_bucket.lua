-- Atomic Redis Token Bucket.
--
-- Ten skrypt implementuje algorytm Token Bucket bezpośrednio w Redisie.
--
-- Dlaczego Lua?
-- Redis wykonuje skrypt Lua atomowo, czyli cała sekwencja:
--   1. odczytaj bucket,
--   2. policz refill,
--   3. sprawdź, czy są tokeny,
--   4. odejmij koszt,
--   5. zapisz nowy stan,
--   6. ustaw TTL
-- jest wykonana jako jedna operacja.
--
-- Dzięki temu unikamy race condition przy wielu równoległych requestach.
--
-- KEYS[1] bucket hash key
-- Przykład:
--   rl:{user:user-123}:eu-central-1:user-limit
--
-- ARGV[1] capacity
-- Maksymalna liczba tokenów w buckecie.
-- Kontroluje burst.
--
-- ARGV[2] refill tokens per second
-- Tempo odnawiania tokenów.
-- Kontroluje długoterminowy limit.
--
-- ARGV[3] cost
-- Ile tokenów kosztuje dany request.
-- Przykład:
--   GET /api/users      -> cost 1
--   POST /api/payments  -> cost 5
--   POST /api/exports   -> cost 50
--
-- ARGV[4] now millis
-- Aktualny czas w milisekundach, przekazany z aplikacji.
--
-- ARGV[5] ttl seconds
-- TTL dla klucza bucketu.
-- Dzięki temu nieaktywne buckety znikają z Redisa.
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_per_second = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])
local now = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

-- Odczytujemy aktualny stan bucketu.
--
-- Bucket jest Redis Hashem z dwoma polami:
--   tokens         -> aktualna liczba tokenów
--   last_refill_ms -> czas ostatniego przeliczenia refill
--
-- HMGET zwraca oba pola naraz.
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_ms')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

-- Jeśli bucket jeszcze nie istnieje, inicjalizujemy go jako pełny.
--
-- To oznacza, że nowy klient może od razu wykorzystać burst
-- do wysokości capacity.
if tokens == nil then
  tokens = capacity
  last_refill = now
end

-- Liczymy, ile czasu minęło od ostatniego refill.
--
-- math.max(0, ...) zabezpiecza przed sytuacją, w której timestamp
-- z aplikacji cofnie się względem poprzedniego requestu.
--
-- W takiej sytuacji nie odejmujemy tokenów za "ujemny czas",
-- tylko przyjmujemy elapsed_ms = 0.
local elapsed_ms = math.max(0, now - last_refill)

-- Przeliczamy upływ czasu na liczbę tokenów do dodania.
--
-- Przykład:
--   refill_per_second = 10
--   elapsed_ms = 500
--   refill = 5 tokenów
local refill = (elapsed_ms / 1000.0) * refill_per_second

-- Uzupełniamy bucket, ale nigdy powyżej capacity.
--
-- To zachowuje właściwość Token Bucket:
-- klient może akumulować tokeny tylko do maksymalnej pojemności.
tokens = math.min(capacity, tokens + refill)

local allowed = 0
local retry_after = 0

-- Jeśli mamy wystarczająco tokenów, request jest dozwolony.
--
-- Odejmujemy cost od aktualnych tokenów.
if tokens >= cost then
  tokens = tokens - cost
  allowed = 1
else
  -- Jeśli tokenów brakuje, request jest odrzucony.
  --
  -- Liczymy, ile sekund potrzeba, żeby brakujące tokeny się odnowiły.
  --
  -- retry_after trafia później do nagłówka Retry-After.
  local missing = cost - tokens
  retry_after = math.ceil(missing / refill_per_second)
end

-- Zapisujemy nowy stan bucketu.
--
-- Ważne:
-- zapisujemy stan także wtedy, gdy request został odrzucony.
-- Dzięki temu last_refill_ms przesuwa się do "now",
-- a kolejne obliczenia refill będą bazować na aktualnym stanie.
redis.call('HMSET', key, 'tokens', tokens, 'last_refill_ms', now)

-- Ustawiamy TTL na bucket.
--
-- Bez tego Redis przechowywałby buckety nieaktywnych klientów bez końca.
redis.call('EXPIRE', key, ttl)

-- Zwracamy wynik do aplikacji Java.
--
-- Format:
--   [1] allowed
--       1 = request dozwolony
--       0 = request zablokowany
--
--   [2] remaining
--       liczba tokenów po operacji, zaokrąglona w dół
--
--   [3] capacity
--       maksymalny limit bucketu
--
--   [4] retry_after
--       po ilu sekundach klient może spróbować ponownie
--
-- Redis Lua używa tablic indeksowanych od 1,
-- ale po stronie Javy result.get(0) odpowiada allowed.
return {allowed, math.floor(tokens), capacity, retry_after}