# Stage 1F — networking aplikacyjny

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** Stage 1F — networking aplikacyjny.
> - **Typowy błąd:** Uznanie pojedynczego wyniku dotyczącego „Stage 1F — networking aplikacyjny” za gwarancję bez sprawdzenia niezmiennika i failure modes.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=ConnectionPoolTest,DnsCacheTest,EphemeralPortPoolTest" test`
> - **Role klas:** brak klasy-kontrprzykładu; pozostałe typy są minimalnymi modelami pojęć opisanych niżej.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Stage 1F — Networking dla backend developera



Backend nie wywołuje „innej usługi”. W praktyce wykonuje łańcuch operacji:

```text
DNS → wybór adresu → pozyskanie socketu z puli albo TCP connect
    → TLS handshake → wysłanie requestu → oczekiwanie na bajty
    → odczyt odpowiedzi → decyzja o ponownym użyciu połączenia
```

Każdy etap ma własny cache, limit, timeout i failure mode. Laboratorium pokazuje,
dlaczego jeden globalny `timeout=5s` oraz ogólne `connection refused` nie są
wystarczającym modelem działania klienta sieciowego.

## Mapa laboratorium

| Kod | Pytanie | Najważniejszy failure mode |
| --- | --- | --- |
| `DnsCache` | kiedy klient zobaczy nowy adres instancji? | stale IP albo negative cache po uruchomieniu usługi |
| `TimeoutPolicy` | co właściwie ogranicza dany timeout? | read trwa dłużej niż deadline całego requestu |
| `TimeoutChainValidator` | kto powinien przerwać pracę pierwszy? | proxy zamyka socket, gdy downstream nadal pracuje |
| `ConnectionPool` | kiedy keep-alive można bezpiecznie wykorzystać ponownie? | stale/half-closed connection albo kolejka oczekująca bez granicy |
| `ProtocolCapacity` | czy request zajmuje połączenie, czy strumień? | zła estymacja liczby połączeń i limitu concurrency |
| `TlsHandshakeCost` | ile RTT dodaje ustanowienie TLS? | handshake na każdym requestcie i retry przed rozgrzaniem puli |
| `EphemeralPortPool` | dlaczego krótkie połączenia wyczerpują klienta? | porty pozostają zajęte przez aktywne tuple i `TIME_WAIT` |
| `RetryAmplification` | ile żądań zobaczy downstream? | klient × proxy × mesh tworzą retry storm |
| `SocketReadProbe` | czym różni się connect od read timeoutu? | TCP istnieje, lecz peer nie wysyła kolejnych bajtów |

## DNS: nazwa nie jest adresem na zawsze

DNS ma kilka warstw cache: resolver systemowy, JVM, biblioteka kliencka, sidecar,
lokalny DNS i authoritative server. TTL odpowiedzi nie gwarantuje, że aplikacja
natychmiast zmieni endpoint:

- istniejące połączenie keep-alive nadal wskazuje stary adres;
- runtime może mieć własny dodatni i ujemny TTL;
- load balancer albo service discovery może zwracać wiele adresów;
- rekord może zmienić się pomiędzy walidacją a połączeniem (TOCTOU/DNS rebinding).

`DnsCacheTest` zmienia odpowiedź resolvera i pokazuje, że stary adres obowiązuje do
końca dodatniego TTL. Osobny negative TTL pokazuje problem klienta, który sprawdził
nazwę chwilę przed uruchomieniem nowej usługi.

W JVM ustawienia takie jak `networkaddress.cache.ttl` są polityką runtime/security,
nie zamiennikiem discovery. Przy rotacji endpointu trzeba skoordynować TTL, draining
starych instancji, lifetime puli i okres nakładania się obu adresów. TTL równy zero
zwiększa zależność od DNS i może przenieść awarię resolvera na każdy request.

## Timeout nie jest jednym zegarem

Najczęściej potrzebne są co najmniej cztery odrębne ograniczenia:

| Timeout | Co obejmuje | Czego nie obejmuje |
| --- | --- | --- |
| pool acquisition | oczekiwanie na wolny connection/stream | TCP connect i odpowiedź |
| connect | ustanowienie TCP | pełny request i zwykle odczyt odpowiedzi |
| TLS handshake | negocjację TLS | logikę downstreamu |
| read/socket | oczekiwanie na dane z ustanowionego socketu | całkowitego czasu wielu kolejnych odczytów |
| request/deadline | cały budżet operacji | automatycznego anulowania pracy po drugiej stronie |

Znaczenie `read timeout` zależy od biblioteki: może ograniczać pojedynczy brak
aktywności, a nie cały response body. Dlatego deadline musi być liczony monotonicznie
i propagowany, a timeout kolejnej fazy ograniczony pozostałym budżetem.

`TimeoutPolicy` po długim connect skraca efektywny read timeout. Samo zamknięcie
socketu przez klienta nie oznacza, że serwer anulował zapytanie SQL, publikację albo
obciążające liczenie. Protokół i aplikacja muszą propagować anulowanie świadomie.

### Łańcuch client → proxy → load balancer → service

Zewnętrzny caller powinien mieć budżet nieco większy niż hop wewnętrzny, aby ten
wewnętrzny zdążył zwrócić kontrolowany błąd i posprzątać zasoby:

```text
client 3000 ms
  proxy 2800 ms
    service 2500 ms
      database statement 2200 ms
```

Margines obejmuje serializację, transport odpowiedzi i cleanup. Jeżeli load balancer
ma 60 s, klient 2 s, a backend 120 s, użytkownik zobaczy timeout po 2 s, podczas gdy
backend może jeszcze długo wykonywać pracę. `TimeoutChainValidator` wykrywa odwróconą
kolejność i brak marginesu, ale prawidłowe wartości muszą wynikać z SLO i pomiarów.

## Keep-alive i connection pooling

Keep-alive amortyzuje TCP/TLS handshake i ogranicza zużycie ephemeral ports, ale pula
jest ograniczonym zasobem, a nie mapą bez końca. Konfiguracja powinna obejmować:

- limit globalny i per destination/route;
- limit oczekujących oraz pool acquisition timeout;
- max idle i max lifetime krótsze od odpowiednich limitów proxy/LB;
- walidację połączenia po błędzie i usunięcie go po niepełnej odpowiedzi;
- metryki leased, idle, pending, connect latency oraz reuse ratio.

`ConnectionPool` używa ponownie wyłącznie połączenia, którego odpowiedź zakończyła się
czysto. Po timeout, RST albo przerwanym body socket nie wraca do puli. W prawdziwym
kliencie nie wystarcza metoda `isConnected()` — mówi ona, że socket kiedyś został
połączony, nie że peer nadal jest osiągalny.

## TLS handshake

TLS kosztuje RTT oraz CPU na kryptografię i walidację certyfikatu. Uproszczony
`TlsHandshakeCost` pokazuje część sieciową:

- pełny TLS 1.2: typowo 2 RTT po ustanowieniu TCP;
- pełny TLS 1.3: typowo 1 RTT;
- wznowienie sesji zmniejsza koszt kryptografii i może ograniczyć negocjację;
- TLS 1.3 0-RTT może wysłać early data, ale jest podatne na replay i nie nadaje się
  automatycznie do nieidempotentnych operacji.

Model nie dolicza DNS, TCP connect, OCSP/certyfikatów ani CPU. Najlepszą optymalizacją
często jest prawidłowy reuse połączeń, a nie osłabianie walidacji TLS.

## HTTP/1.1 kontra HTTP/2

W typowym kliencie HTTP/1.1 jedno aktywne żądanie dzierżawi jedno połączenie; pipelining
jest rzadko używany. HTTP/2 multipleksuje wiele strumieni na jednym połączeniu, ale:

- peer negocjuje `MAX_CONCURRENT_STREAMS`;
- limit puli nadal ma znaczenie;
- utrata pakietu może blokować strumienie współdzielące jedno połączenie TCP;
- jedno przeciążone połączenie nie zawsze jest lepsze niż kilka kontrolowanych;
- HTTP/2 do proxy nie dowodzi, że proxy używa HTTP/2 do downstreamu.

`ProtocolCapacity` liczy połączenia potrzebne do danego concurrency. To model
pojemności, nie benchmark: realny wynik zależy również od rozmiaru odpowiedzi,
flow control, RTT, TLS, CPU i implementacji serwera.

## Ephemeral ports i wyczerpanie socketów

Połączenie TCP identyfikuje tuple source IP/port i destination IP/port. Duża liczba
krótkich połączeń do tego samego celu zużywa ephemeral ports, a po aktywnym close port
może pozostać w `TIME_WAIT`. NAT dodatkowo współdzieli pulę między instancjami.

`EphemeralPortPoolTest` używa zakresu dwóch portów: oba połączenia zamykają się, ale
nowe nie powstanie do wygaśnięcia `TIME_WAIT`. Produkcyjna kolejność działań to:

1. potwierdzić stany socketów, tempo nowych połączeń i destination tuple;
2. naprawić connection reuse, concurrency i retry;
3. sprawdzić limity file descriptors, NAT/SNAT i conntrack;
4. dopiero potem świadomie zmieniać zakres portów lub parametry kernela.

Zwiększenie port range nie naprawia klienta otwierającego nowe TLS connection dla
każdego requestu — tylko odsuwa moment awarii.

## Retry na wielu warstwach

Trzy próby klienta, trzy proxy i trzy service mesha mogą dać 27 wywołań downstreamu.
`RetryAmplification` pokazuje iloczyn, nie sumę. Właściciel retry powinien być jeden,
a polityka musi uwzględniać:

- pozostały deadline i wspólny retry budget;
- idempotencję albo idempotency key;
- backoff z jitterem;
- przyczynę błędu — nie każdy timeout oznacza, że operacja nie została wykonana;
- limit globalnego dodatkowego ruchu podczas awarii.

Proxy lub mesh nie zna automatycznie semantyki biznesowej POST. Retry po read timeout
może powtórzyć operację, którą serwer zakończył, lecz odpowiedź zginęła w sieci.
Pełny pipeline przeciążenia znajduje się w
[Stage 3A](../../stage_3/block_a/implementation/overload/README.md).

## Half-open, FIN, RST i blackhole

„Połowicznie zerwane połączenie” może oznaczać różne rzeczy:

- FIN — peer poprawnie zamknął kierunek wysyłania; odczyt zwraca EOF (`-1`);
- RST — system jawnie resetuje połączenie; operacja zwykle kończy się błędem;
- blackhole — pakiety znikają bez FIN/RST, więc aplikacja dowiaduje się dopiero przez
  read/write timeout, TCP retransmission timeout, keepalive lub heartbeat;
- half-close — jeden kierunek jest zamknięty, drugi nadal może przesyłać dane.

`SocketFailureSemanticsTest` używa prawdziwego lokalnego `ServerSocket`. Pokazuje, że
udany connect może zakończyć się read timeoutem oraz że kontrolowany FIN jest EOF,
nie timeoutem. Lokalny test nie symuluje blackhole — do tego potrzebny jest fault
injection na poziomie proxy/firewalla, np. Toxiproxy albo reguły sieciowe.

TCP keepalive wykrywa martwego peera po czasie, ale zwykle nie zastępuje krótkiego
deadline requestu. Długowieczne protokoły często potrzebują heartbeatów aplikacyjnych,
bo tylko one potwierdzają, że event loop i logika peera nadal działają.

## Testy i uruchomienie

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=DnsCacheTest,ConnectionPoolTest,TimeoutPolicyTest,RetryAmplificationTest,ProtocolAndTlsTest,EphemeralPortPoolTest,SocketFailureSemanticsTest" test
```

Testy są szybkie i nie korzystają z Internetu. Czas jest sterowany przez
`MutableClock`; wyłącznie test semantyki TCP otwiera socket na loopbacku.

## Diagnostyka na Windows

```powershell
Resolve-DnsName example.com
Test-NetConnection example.com -Port 443
Get-NetTCPConnection | Group-Object State
curl.exe -v --http2 https://example.com
openssl s_client -connect example.com:443 -servername example.com
```

W kontenerze lub produkcji narzędzia mogą nie być zainstalowane. Diagnostyka powinna
łączyć metryki klienta, log błędu z fazą (`dns`, `pool`, `connect`, `tls`, `read`),
trace oraz dane systemowe. Sam komunikat „timeout” bez fazy i elapsed time utrudnia
odróżnienie przeciążonej puli od wolnego downstreamu.

## Granice i powiązania

- SSRF, allowlista egress i DNS rebinding: [Stage 2D](../../stage_2/block_d/README.md).
- Deadline, anulowanie i przerwanie w Javie: [Stage 1A](../block_a/cancel/README.md).
- Konfiguracja proxy, probes i graceful shutdown: [Stage 2C](../../stage_2/block_c/README.md).
- Metryki, tracing i alertowanie: [Stage 3B](../../stage_3/block_b/README.md).
- Laboratorium nie zastępuje packet capture, testu realnego LB/mesh ani pomiaru na
  docelowym kernelu, NAT i bibliotece HTTP.
