# Alerting i runbooki w systemie observability

## Cel dokumentu

Ten dokument opisuje teoretyczne podstawy projektowania alertingu i runbooków dla aplikacji backendowej, na przykładzie usługi `checkout-api`. Nie chodzi tutaj o samo napisanie reguł PromQL ani o techniczne wygenerowanie plików YAML. Ważniejsza jest zmiana sposobu myślenia: alert nie jest wykresem z progiem, lecz kontraktem operacyjnym między systemem a osobą, która ma zareagować pod presją czasu.

Dobrze zaprojektowany alert powinien informować o realnym objawie widocznym z perspektywy użytkownika, prowadzić do właściwego dashboardu lub runbooka, mieć jasne znaczenie biznesowe i techniczne oraz dawać responderowi konkretną ścieżkę pierwszych działań. Jeśli alert mówi tylko, że „coś jest czerwone”, ale nie wiadomo, czy trzeba budzić człowieka, otworzyć ticket, wykonać rollback, sprawdzić trace czy zignorować chwilowy szum, to taki alert nie spełnia swojej funkcji.

W podejściu SRE i Prometheus zdrowy alerting zaczyna się od symptomów, a nie od przyczyn. System może mieć wiele wewnętrznych stanów, które wyglądają podejrzanie, ale nie wpływają na użytkownika. Z drugiej strony użytkownik może doświadczać wysokiej latencji lub błędów nawet wtedy, gdy pojedyncze komponenty infrastruktury wydają się działać poprawnie. Dlatego podstawowym punktem startowym powinny być golden signals: latency, traffic, errors i saturation. Alert powinien odpowiadać na pytanie, czy użytkownik realnie cierpi, a dopiero potem prowadzić do diagnozy przyczyny.

## Alert jako kontrakt operacyjny

Alert nie powinien być traktowany wyłącznie jako techniczna reguła w Prometheusie. Reguła PromQL jest tylko jednym fragmentem większej całości. Pełny alert składa się z warunku detekcji, czasu trwania, etykiet służących do routingu, adnotacji z kontekstem oraz powiązanego runbooka. Dopiero ten zestaw tworzy praktyczny mechanizm reagowania na incydent.

Warunek detekcji powinien być prosty i zrozumiały. W przypadku `checkout-api` dobrym przykładem jest alert na wysokie p95 latencji dla `POST /orders/:id/pay` przy istotnym ruchu. Taki alert opisuje objaw użytkownika: operacja płatności trwa za długo. Nie zakłada jeszcze, czy winny jest Redis, PostgreSQL, provider płatności, CPU, garbage collector czy ostatni deploy. To jest celowe. Alert ma wskazać, że istnieje problem wymagający reakcji, a nie udawać pełną analizę przyczyny źródłowej.

Czas `for` w regule alertu ogranicza przypadkowe krótkie skoki, które nie wymagają budzenia człowieka. Jeśli p95 przekracza próg przez kilkanaście sekund, może to być naturalna zmienność systemu. Jeśli utrzymuje się przez dziesięć minut przy realnym ruchu, sytuacja jest poważniejsza. Z kolei `keep_firing_for` pomaga ograniczać flapping, czyli szybkie przełączanie alertu między stanem firing i resolved. W praktyce flapping jest kosztowny operacyjnie, bo zwiększa hałas i osłabia zaufanie zespołu do alertingu.

Alert powinien być actionable. To słowo jest łatwe do nadużycia, ale w praktyce oznacza bardzo konkretną rzecz: osoba, która dostaje alert, powinna wiedzieć, co może zrobić w pierwszych minutach. Jeśli alert nie prowadzi do żadnej decyzji ani działania, prawdopodobnie nie powinien być pagerem. Może być ticketem, sygnałem dashboardowym albo materiałem do analizy trendów, ale nie powodem do natychmiastowego przerwania pracy lub snu.

## Labels i annotations

W alertingu labels i annotations pełnią różne role. Labels są częścią tożsamości alertu i służą do routingu, grupowania, deduplikacji oraz wyciszania. Annotations są przeznaczone dla człowieka i niosą opis sytuacji. Mieszanie tych dwóch warstw prowadzi do problemów operacyjnych.

Labels powinny być stabilne i niskokardynalne. Typowe przykłady to `severity`, `priority`, `team`, `service`, `cluster` i `environment`. Na ich podstawie Alertmanager lub Grafana mogą zdecydować, czy alert ma trafić do backend-pagera, ticketingu, zespołu bazodanowego czy kanału ostrzegawczego. Nie należy umieszczać w labels wartości takich jak `trace_id`, `request_id`, `user_id`, adres e-mail, pełny URL, pełne zapytanie SQL czy komunikat wyjątku. Takie wartości są wysokokardynalne albo wrażliwe i niszczą semantykę routingu oraz grupowania.

Annotations powinny odpowiadać na pytanie, co człowiek ma wiedzieć natychmiast po otwarciu alertu. Dobre minimum to `summary`, `description`, `runbook_url` i często `dashboard_url`. Summary powinno być krótkie i czytelne. Description może zawierać próg, czas trwania i informację, dlaczego alert odpalił. Runbook URL powinien prowadzić do instrukcji pierwszych działań, a dashboard URL do widoku golden signals lub odpowiedniego obszaru diagnostycznego.

Warto zauważyć, że annotations nie powinny zastępować runbooka. Krótki opis w alercie jest przydatny, ale nie wystarczy w stresującej sytuacji incydentowej. Alert powinien być wejściem do procesu, a nie całym procesem.

## Routing alertów

Routing w Alertmanagerze opiera się na labels. To dlatego projektowanie labels jest decyzją operacyjną, a nie kosmetyczną. Jeśli alert ma `team="backend"` i `severity="page"`, może trafić do backend-pagera. Jeśli ma `severity="ticket"`, może zostać przekierowany do systemu zgłoszeń. Jeśli dotyczy usługi bazodanowej, może trafić do zespołu odpowiedzialnego za bazę.

Grupowanie alertów ma równie duże znaczenie. Jeśli awaria jednej usługi powoduje dziesiątki alertów dla różnych tras, instancji i regionów, responder nie powinien dostać dziesiątek niezależnych powiadomień bez kontekstu. Grupowanie po `cluster`, `service` i `alertname` pomaga zredukować hałas i pokazać, że wiele sygnałów może dotyczyć jednego incydentu. Jednocześnie nie należy grupować zbyt agresywnie, bo można ukryć różnice między regionami lub usługami.

Dobrze zaprojektowany routing powinien wspierać sposób pracy organizacji. Jeśli zespół backendowy odpowiada za `checkout-api`, a zespół bazodanowy za platformę PostgreSQL, to labels i matchers powinny odzwierciedlać tę odpowiedzialność. W przeciwnym razie alerty będą trafiały do ludzi, którzy nie mają kontekstu ani uprawnień do działania.

## Runbook jako część observability

Runbook jest praktycznym przedłużeniem alertu. Bez runbooka alert często kończy się chaotyczną analizą, szczególnie jeśli incydent wydarza się poza godzinami pracy albo dotyczy osoby mniej doświadczonej w danym systemie. Dobry runbook nie musi znać pełnej przyczyny źródłowej. Jego rolą jest uporządkowanie pierwszych minut: potwierdzić wpływ, zawęzić scope, sprawdzić najważniejsze sygnały, wykonać bezpieczną mitigację i zweryfikować poprawę.

Runbook powinien zaczynać się od sekcji detekcji. Ta sekcja mówi, jaki alert go uruchamia, jaki dashboard otworzyć i jakie sygnały są najważniejsze. Dla skoku latencji będą to p95, p99, error rate, RPS, inflight requests, CPU, GC, kolejki oraz latencja downstreamów. Dla problemu z bazą istotne będą timeouty, pending requests w poolu, p95 operacji DB i trace’y pokazujące dominujący span PostgreSQL. Dla awarii Redis ważny jest spadek hit ratio, wzrost błędów lub timeoutów cache oraz wtórny wzrost obciążenia bazy.

Następna sekcja powinna opisywać pierwsze działania. Nie chodzi o długą listę możliwych teorii, lecz o kilka kroków, które pomagają szybko zmniejszyć niepewność. W przypadku latencji pierwszym krokiem jest zawężenie scope’u: route, region, cluster, wersja usługi i okno czasu. Następnie należy sprawdzić, czy problem dotyczy tylko latencji, czy także error rate. Potem warto sprawdzić saturation i przejść z exemplaru lub punktu na wykresie do trace’a. Jeśli problem koreluje z deployem, rollback często jest lepszą pierwszą mitigacją niż długa analiza RCA.

Runbook powinien zawierać również konkretne zapytania PromQL i komendy operacyjne. Nie dlatego, że responder nie zna PromQL albo Kubernetesa, lecz dlatego, że w trakcie incydentu liczy się powtarzalność. Każda minuta spędzona na przypominaniu sobie właściwego zapytania lub szukaniu komendy zwiększa czas do mitigacji. Runbook jest narzędziem redukcji tarcia.

## Mitigation-first zamiast RCA-first

W obsłudze incydentów łatwo wpaść w pułapkę natychmiastowego szukania przyczyny źródłowej. To jest zrozumiałe, ale często nieoptymalne. W trakcie aktywnego incydentu pierwszym celem jest ograniczenie wpływu na użytkownika. RCA jest ważne, ale zwykle powinno nastąpić po stabilizacji systemu.

Dla `checkout-api` przykładową mitigacją może być rollback ostatniego deploya, traffic shift do zdrowego regionu, czasowe wyłączenie problematycznej funkcji feature flagą, zwiększenie limitów lub liczby replik, ograniczenie retry, włączenie trybu degradacji albo przejście na read-only mode. To nie zawsze usuwa przyczynę, ale może szybko zmniejszyć skutki. Dopiero po odzyskaniu akceptowalnej latencji i success rate warto przejść do głębszej analizy.

To podejście wymaga dyscypliny w kryteriach zamknięcia incydentu. Incydent nie jest zakończony dlatego, że pod się zrestartował, Redis odpowiada na PING albo baza przyjmuje połączenie. Incydent można uznać za operacyjnie opanowany dopiero wtedy, gdy wróciły metryki użytkownika: p95 i p99 są w akceptowalnym zakresie, error rate wrócił do baseline, success rate jest stabilny, a alert nie ma już symptomatycznej podstawy do odpalania.

## Skok latencji

Skok latencji jest jednym z najczęstszych i najtrudniejszych incydentów, bo sam komunikat „system jest wolny” prawie nic nie znaczy. Runbook powinien wymuszać konkretyzację. Trzeba ustalić, która trasa jest wolna, od kiedy, w którym regionie, przy jakim ruchu i dla której wersji usługi. Następnie trzeba sprawdzić, czy latencja rośnie razem z błędami, czy system jedynie odpowiada wolniej.

Jeśli rośnie również error rate, sytuacja może oznaczać timeouty, niedostępność downstreamu albo przeciążenie. Jeśli error rate pozostaje stabilny, a p95 lub p99 rosną, bardziej prawdopodobne są saturation, kolejki, wolne zapytania, locki, problem z providerem zewnętrznym albo regresja wydajnościowa. Trace’y i exemplars mają tu szczególną wartość, bo pozwalają przejść z agregatu metrycznego do konkretnego requestu i zobaczyć, który hop zużył czas.

W dobrze zbudowanym systemie analiza skoku latencji powinna zaczynać się od golden signals, a następnie przechodzić do najwolniejszego hopa. Jeśli trace pokazuje Redis, otwieramy runbook Redis. Jeśli PostgreSQL, runbook DB. Jeśli provider płatności, sprawdzamy external API i politykę timeoutów. Jeśli nie ma jednego winnego hopa, trzeba sprawdzić deploy, saturation, logi błędów, zmiany ruchu i konfigurację infrastruktury.

## DB down

Problem z bazą danych nie zawsze oznacza, że baza jest całkowicie niedostępna. Często objawem jest wyczerpanie puli połączeń, wzrost pending requests, timeouty przy pobieraniu połączenia, wolne zapytania lub problem z autoryzacją. Z perspektywy użytkownika wszystkie te przypadki mogą wyglądać podobnie: requesty są wolne albo kończą się błędami.

Runbook DB powinien zaczynać od potwierdzenia wpływu user-facing. Sam czerwony exporter albo pojedynczy błąd connectivity nie zawsze uzasadnia page. Jeśli jednak rośnie error rate na trasach zależnych od DB, p95 API przekracza próg albo trace’y pokazują dominujący span PostgreSQL, trzeba działać. Pierwsze kroki powinny obejmować sprawdzenie reachability z perspektywy aplikacji, autoryzacji, DNS, failovera, stanu puli połączeń oraz timeoutów.

Ważnym elementem mitigacji jest ostrożność z retry. Retry może pomóc przy krótkotrwałych błędach, ale przy przeciążonej bazie często pogarsza sytuację. Jeśli wiele instancji aplikacji zaczyna agresywnie ponawiać zapytania, system może wejść w kaskadową awarię. Dlatego runbook powinien przewidywać ograniczenie retry, degraded mode, read-only mode albo czasowe odcięcie części funkcjonalności.

## Redis down

Awaria Redis jest szczególnie zdradliwa, bo cache często jest traktowany jako zależność pomocnicza, a w praktyce może chronić bazę przed nadmiernym ruchem. Spadek hit ratio albo timeouty Redis mogą spowodować, że ruch, który wcześniej kończył się w cache, zacznie trafiać do PostgreSQL. Wtedy pierwotny problem z cache może szybko stać się problemem z bazą i całym API.

Runbook Redis powinien więc sprawdzać nie tylko sam Redis, lecz także wtórne skutki. Pierwsze pytanie brzmi: czy awaria cache wpływa na `checkout-api`? Drugie: czy baza przyjmuje dodatkowe obciążenie po cache missach? Trzecie: czy fallback jest bezpieczny, czy grozi cache stampede? Dopiero potem warto szczegółowo analizować endpoint, auth, DNS, replikację czy konfigurację Redis.

Mitigacja przy problemach z cache musi być ostrożna. Prosty fallback do bazy może być poprawny funkcjonalnie, ale niekoniecznie bezpieczny wydajnościowo. Lepsze strategie obejmują request coalescing, limity równoległości, stale cache, circuit breaker, backoff albo czasowe ograniczenie mniej krytycznych operacji. Kryterium zakończenia incydentu powinno obejmować nie tylko powrót Redis, ale też normalizację hit ratio, DB p95 i API p95.

## Triage i najwolniejszy hop

Zdrowy przepływ obsługi alertu można opisać jako serię pytań. Najpierw trzeba sprawdzić, czy ruch jest istotny. Alert przy minimalnym ruchu może nie uzasadniać pagera, bo percentyle na małej próbce bywają mylące. Następnie trzeba potwierdzić, czy wpływ jest user-facing. Jeśli nie, alert może zostać zdegradowany do warning albo ticketu.

Jeżeli wpływ jest realny, responder powinien otworzyć golden signals i znaleźć exemplar lub trace z problematycznego okna. To pozwala ustalić najwolniejszy lub najczęściej zawodzący hop. Jeśli jest nim Redis, naturalnym kolejnym krokiem jest runbook Redis. Jeśli baza, runbook DB. Jeśli external API, trzeba sprawdzić timeouty, retry, circuit breaker i ewentualnie degraded mode. Jeśli nie ma jasnego winnego hopa, trzeba sprawdzić deploy, saturation, logi i ogólny profil ruchu.

Warto podkreślić, że ten proces nie jest automatycznym RCA. To jest proces redukcji niepewności i wyboru pierwszej bezpiecznej ścieżki działania. Dobry system observability nie obiecuje, że sam znajdzie przyczynę. Daje natomiast dowody, które pozwalają szybciej przechodzić od objawu do hipotezy, od hipotezy do mitigacji i od mitigacji do weryfikacji.

## Relacja między alertami, dashboardami, trace’ami i runbookami

Alerting nie powinien istnieć osobno od reszty observability. Alert uruchamia uwagę człowieka. Dashboard pokazuje kontekst metryk i trendów. Exemplars i trace’y pozwalają przejść od agregatu do konkretnego requestu. Logi dostarczają szczegółowego kontekstu zdarzeń. Runbook prowadzi przez pierwsze decyzje operacyjne. Dopiero razem te elementy tworzą spójny system reagowania na incydenty.

Jeśli alert nie prowadzi do dashboardu, responder traci czas na szukanie właściwego widoku. Jeśli dashboard nie pozwala przejść do trace’a, trudno znaleźć konkretny request reprezentujący problem. Jeśli trace nie koreluje się z logami, brakuje szczegółowego kontekstu. Jeśli nie ma runbooka, każdy incydent jest obsługiwany inaczej, zależnie od doświadczenia osoby na dyżurze. Dlatego alerting i runbooki należy projektować razem z metrykami, tracingiem i logami, a nie jako osobną warstwę doklejoną na końcu.

## Dlaczego klasy Java są przydatne dla tego konceptu

Modelowanie alertów i runbooków w Java nie oznacza, że aplikacja produkcyjna ma dynamicznie tworzyć reguły Prometheusa w runtime. Bardziej sensownym zastosowaniem jest traktowanie klas jako typed source of truth dla konfiguracji operacyjnej. Klasy takie jak `PrometheusAlertRule`, `AlertLabels`, `AlertAnnotations`, `AlertmanagerConfig` i `Runbook` pomagają wymusić spójność między alertami, routingiem i dokumentacją.

Dzięki temu można uniknąć sytuacji, w której alert wskazuje nieistniejący runbook, routing używa innej nazwy zespołu niż labels w regule, a dokumentacja opisuje inne zapytania niż dashboard. Typowany model może walidować, że każdy alert ma severity, team, service, summary, description i runbook_url. Może też blokować niebezpieczne labels, takie jak `trace_id` albo `request_id`. To nie jest pełne rozwiązanie organizacyjne, ale jest praktycznym zabezpieczeniem przed dryfem konfiguracji.

W takim podejściu generowanie YAML dla Prometheusa, Alertmanagera i Markdown dla runbooków jest efektem ubocznym spójnego modelu. Najważniejszy jest kontrakt: każdy alert musi mieć właściciela, próg, uzasadnienie, routing, kontekst i instrukcję działania.

## Kryterium jakości

Dobry alerting dla `checkout-api` można ocenić bardzo praktycznie. Jeśli odpala się alert wysokiej latencji, responder powinien od razu wiedzieć, czy ma otworzyć dashboard golden signals, jaki runbook jest właściwy, jak sprawdzić p95 i p99, jak ocenić error rate, gdzie znaleźć trace z exemplaru i jakie działania mitigacyjne są akceptowalne w pierwszych minutach.

Jeżeli alert wymaga najpierw długiej interpretacji, pytania „czy to nasz alert?”, szukania właściciela, ręcznego odtwarzania PromQL albo zgadywania, czy problem dotyczy użytkowników, to system alertingu jest niedojrzały. Jeśli natomiast alert prowadzi do jasnego procesu: objaw, scope, golden signals, najwolniejszy hop, runbook, mitigacja, weryfikacja na tych samych metrykach, wtedy alerting staje się częścią observability, a nie tylko generatorem hałasu.

## Podsumowanie

Alerting i runbooki są operacyjną warstwą observability. Metryki, trace’y i logi dostarczają dowodów, ale to alert decyduje, kiedy człowiek ma przerwać pracę i zareagować. Runbook decyduje, czy ta reakcja będzie chaotyczna, czy powtarzalna.

Najważniejsze zasady są proste, ale wymagają konsekwencji. Alertuj na symptomy użytkownika, nie na każdą możliwą przyczynę techniczną. Używaj labels do routingu i annotations do kontekstu. Każdy page powinien mieć runbook. Runbook powinien zaczynać od mitigacji i kończyć się weryfikacją na tych samych metrykach, które uruchomiły alert. Nie zamykaj incydentu dlatego, że komponent wygląda zdrowo; zamknij go dopiero wtedy, gdy wróciły metryki użytkownika.

Właśnie to odróżnia dojrzały alerting od monitoringu, który tylko świeci na czerwono.
