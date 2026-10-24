# Doświadczenie, decyzje i AI

[Spis rozdziałów](README.md)

To schematy odpowiedzi do uzupełnienia własnymi faktami. Nie przypisują czytelnikowi projektów, wyników ani doświadczenia, którego nie ma. Brak kontaktu z technologią warto powiedzieć wprost i oddzielić praktykę produkcyjną od laboratorium.

<!-- question-toc:start -->
## Spis pytań

- [EXP-01. Jak opowiedzieć szczegółowo o jednym projekcie?](#exp-01-jak-opowiedzieć-szczegółowo-o-jednym-projekcie)
- [EXP-02. Jak opisać zmianę projektu pod wpływem kompromisu?](#exp-02-jak-opisać-zmianę-projektu-pod-wpływem-kompromisu)
- [EXP-03. Jak opowiedzieć o awarii, za którą brałem odpowiedzialność?](#exp-03-jak-opowiedzieć-o-awarii-za-którą-brałem-odpowiedzialność)
- [EXP-04. Jak opisać spór techniczny w zespole?](#exp-04-jak-opisać-spór-techniczny-w-zespole)
- [EXP-05. Jak przedstawić własne usprawnienie wydajności?](#exp-05-jak-przedstawić-własne-usprawnienie-wydajności)
- [EXP-06. Jak zaplanować pierwsze 90 dni pracy nad niestabilną platformą?](#exp-06-jak-zaplanować-pierwsze-90-dni-pracy-nad-niestabilną-platformą)
- [EXP-07. Jak modernizować legacy bez zatrzymania produkcji?](#exp-07-jak-modernizować-legacy-bez-zatrzymania-produkcji)
- [EXP-08. Jak wykorzystuję AI w codziennym programowaniu?](#exp-08-jak-wykorzystuję-ai-w-codziennym-programowaniu)
- [EXP-09. Kiedy kod z AI był błędny i jak go wykryłem?](#exp-09-kiedy-kod-z-ai-był-błędny-i-jak-go-wykryłem)
- [EXP-10. Jak projektować funkcję korzystającą z nieprzewidywalnego wyniku LLM?](#exp-10-jak-projektować-funkcję-korzystającą-z-nieprzewidywalnego-wyniku-llm)
- [EXP-11. Jak ograniczać latency i koszt funkcji LLM?](#exp-11-jak-ograniczać-latency-i-koszt-funkcji-llm)
- [EXP-12. Jaka jest wartość inżyniera, gdy AI generuje kod?](#exp-12-jaka-jest-wartość-inżyniera-gdy-ai-generuje-kod)
- [EXP-13. Co gdy model nie działa, odpowiada wolno albo podaje bzdury?](#exp-13-co-gdy-model-nie-działa-odpowiada-wolno-albo-podaje-bzdury)
- [EXP-14. Jak ocenić, czy funkcja AI działa na produkcji?](#exp-14-jak-ocenić-czy-funkcja-ai-działa-na-produkcji)
- [EXP-15. Kiedy świadomie nie używać AI?](#exp-15-kiedy-świadomie-nie-używać-ai)
- [EXP-16. Jak chronić prywatność i bezpieczeństwo funkcji AI?](#exp-16-jak-chronić-prywatność-i-bezpieczeństwo-funkcji-ai)
- [EXP-17. Jak zaprojektować RAG nad 10 mln dokumentów?](#exp-17-jak-zaprojektować-rag-nad-10-mln-dokumentów)
<!-- question-toc:end -->

## EXP-01. Jak opowiedzieć szczegółowo o jednym projekcie?

Zaczynam od wyniku i problemu użytkownika, potem przedstawiam swój zakres, skalę i ograniczenia. Rozwijam jeden przepływ: źródło prawdy, decyzję techniczną, alternatywę i zachowanie przy błędzie. Kończę rzeczywistym pomiarem oraz tym, co zmieniłbym dziś.

Pomocny szkielet do uzupełnienia: „Celem było [problem]. Odpowiadałem za [zakres]. Najważniejszym ograniczeniem było [czas/spójność/koszt]. Wybraliśmy [decyzja], bo [dowód]. Obserwowaliśmy [wynik i okres pomiaru].” To struktura opowieści, nie gotowa historia do przypisania sobie.

Wynik zespołu oddzielam od własnego wkładu. Milion użytkowników nie określa RPS ani ruchu szczytowego, więc podaję jednostki, definicję metryki i granice wiedzy. Jeżeli brakowało pomiaru, mówię to wprost. Lista technologii bez ich roli i wpływu nie wyjaśnia jakości decyzji.

<!-- follow-up:EXP-01 -->
**Sprawdź rozumienie:** Nie masz liczbowych metryk albo pełnego ownershipu projektu. Jak odpowiedzieć wiarygodnie?

<details>
<summary>Kierunek odpowiedzi</summary>

Powiedz, jaki był własny zakres i jakie dowody rzeczywiście istnieją. Oddziel wynik zespołu od swojej roli, nie zastępuj braku danych wymyślonym procentem ani historią produkcyjną.

</details>

## EXP-02. Jak opisać zmianę projektu pod wpływem kompromisu?

Podaję pierwotne założenie, nowy fakt i porównywane opcje. Wyjaśniam, co zyskałem oraz z czego świadomie zrezygnowałem, np. świeżość projekcji w zamian za dostępność odczytu. Pokazuję sposób bezpiecznej migracji i kryterium ponownego rozważenia decyzji.

Nie opowiadam o „najlepszym wzorcu” bez kontekstu. Dobry przykład może być mały: rezygnacja z cache, prostsza transakcja lub ograniczenie fan-out. Wynik potwierdzam pomiarem albo jawnym ograniczeniem wiedzy.

Dobra historia rozdziela moment decyzji od wiedzy zdobytej później. „Zrobiliśmy źle” jest mniej informacyjne niż „założyliśmy 100/s, pomiar pokazał gorący klucz i 2000/s, dlatego zmieniliśmy model”. Opisuję też alternatywę odrzuconą i powód, np. koszt operacyjny lub trudniejszy rollback.

ADR może zawierać kontekst, opcje, wybór, konsekwencje i warunek rewizji. To pomaga pokazać, że decyzja była rozsądna w danych ograniczeniach, a zmiana wynikała z nowych dowodów. Nie trzeba wymyślać wielkiej przebudowy: mały trafny kompromis z dobrym pomiarem jest wartościowym przykładem.

## EXP-03. Jak opowiedzieć o awarii, za którą brałem odpowiedzialność?

Historię awarii porządkuję jako wykrycie → wpływ → mitigation → recovery → przyczyna → działania. Podaję swoją rzeczywistą rolę, dowody zawężające problem i kryteria potwierdzające odzyskanie usługi oraz integralność danych. Restart lub rollback jest mitigation, nie automatycznie trwałą naprawą.

Własną historię sprawdzam pytaniami: jak ustalono zakres uszkodzonych danych, skąd było wiadomo, że cofnięcie jest bezpieczne i kto potwierdził koniec incydentu? Jeśli byłem obserwatorem albo odpowiadałem tylko za trace/analizę, nie dopisuję sobie pełnego ownershipu.

Postmortem opisuje brakujące zabezpieczenie i działanie z właścicielem, bez szukania winnego. „Będziemy uważać” zastępuję konkretem: limit kolejki, alarm na wiek outbox, test recovery, restore drill lub poprawa runbooka. Samo „więcej testów” nie odpowiada na każdą przyczynę operacyjną.

## EXP-04. Jak opisać spór techniczny w zespole?

Przedstawiam wspólny cel, argumenty obu stron i kryteria decyzji. Wyjaśniam, czy pomogły eksperyment, dane, ADR albo odwracalny etap wdrożenia. Wynik może oznaczać także przyjęcie propozycji drugiej osoby.

Nie oceniam ludzi przez pryzmat wybranego frameworka. Pokazuję, jak po decyzji współpracowałem i co zespół z niej wyniósł. Jeśli konflikt pozostał nierozwiązany, opisuję realny dalszy proces, zamiast dopisywać idealne zakończenie.

Strukturę rozmowy pomaga oprzeć na ograniczeniach: czy priorytetem była latency, szybkość dostarczenia, spójność czy koszty utrzymania. Dwie osoby mogą proponować różne rozwiązania, bo przyjęły inne założenia. Najpierw te założenia ujawniam, dopiero potem porównuję technologie.

Opisuję sposób rozstrzygnięcia i dalsze zachowanie po decyzji. Eksperyment ma zdefiniowany wynik, a nie dowodzi z góry mojej racji. Jeżeli nie ma czasu na pełny test, można wybrać odwracalny etap z kryterium ponownej oceny. Spór nie powinien zamieniać się w historię o pokonaniu „mniej kompetentnego” zespołu.

## EXP-05. Jak przedstawić własne usprawnienie wydajności?

Dowód usprawnienia zawiera endpoint, workload, baseline p50/p99 i error rate, potwierdzony bottleneck, zmianę, wynik oraz koszt uboczny. Wszystkie liczby pochodzą z rzeczywistego pomiaru albo są wyraźnie oznaczonym eksperymentem.

Wpływ oceniam przy porównywalnych danych, ruchu i limitach. Cache może poprawić medianę, a pogorszyć ogon podczas stampede; indeks odczyt, a spowolnić import; async skrócić HTTP, ale zwiększyć wiek procesu biznesowego. Dlatego wynik obejmuje ukończoną operację i zużycie zasobów, nie tylko łatwą do poprawienia metrykę pośrednią.

Jeśli naraz zmieniono cały stos, nie przypisuję poprawy jednej bibliotece. Opisuję, co pomiar rzeczywiście rozstrzyga i czego nie dowodzi. Test regresji wydajności utrzymuje reprezentatywny mix operacji i warunki zasobowe. Taka historia jest bardziej informacyjna niż procent przyspieszenia bez jednostki i definicji.

## EXP-06. Jak zaplanować pierwsze 90 dni pracy nad niestabilną platformą?

Pierwsze 30 dni: critical journeys, właściciele, ryzyka, baseline SLO, dostęp do diagnostyki i bezpieczny rollback. Dni 31–60: usuwanie największych powtarzalnych awarii, krótszy feedback CI i testowanie backup/restore. Dni 61–90: ugruntowanie standardów, capacity test i zmierzona poprawa wdrożeń.

To priorytety do uzgodnienia, nie sztywny kalendarz. Wybieram problemy według wpływu i ryzyka, angażuję zespół i nie zaczynam od pełnego rewrite. Każde usprawnienie ma mierzalny cel i właściciela.

Plan 90 dni powinien zacząć się od rozmów i mapy odpowiedzialności, nie listy narzuconych narzędzi. Wybieram kilka największych źródeł ryzyka i ustalam baseline: czas recovery, częstotliwość błędnych wydań, lead time i terminowość krytycznych operacji.

Działania mają właściciela i mały sprawdzalny rezultat, np. pierwszy udany restore z kontrolą danych albo możliwość cofnięcia wydania w określonym czasie. Duży rewrite może przesunąć feedback o miesiące i zwiększyć ryzyko. Jeśli produkcja płonie codziennie, część planu przesuwa się na ograniczenie awarii, ale nadal zachowuje mierzalny cel.

## EXP-07. Jak modernizować legacy bez zatrzymania produkcji?

Najpierw poznaję krytyczne zachowania i zabezpieczam je characterization tests. Wprowadzam seam/port wokół wybranego problemu, przenoszę mały przepływ i porównuję wyniki przez shadow lub stopniowe przełączenie. Nowa granica ma jasnego właściciela danych.

Oddzielam refaktoryzację od zmiany reguł, zachowuję kompatybilność kontraktów i możliwość rollbacku. Ekstrakcja mikroserwisu nie jest celem samym w sobie. Czasem największą poprawę daje modularny monolit i usunięcie niestabilnej zależności.

Characterization test zapisuje obserwowane zachowanie legacy, także dziwne, które może być faktycznym kontraktem klienta. Nie oznacza uznania każdego błędu za docelową regułę; zmianę semantyki wydzielam i uzgadniam osobno. To pozwala refaktoryzować bez nieświadomego rewrite zachowania.

Shadow comparison działa bez podwajania skutków: drugi wariant może obliczać wynik, ale nie wysyłać drugiego przelewu. Nowy adapter przełączam flagą lub routingiem na mały zakres. Dane wymagają expand/contract i planu powrotu. Sukcesem jest mniejszy koszt zmiany oraz stabilniejszy przepływ, nie sam wzrost liczby usług.

## EXP-08. Jak wykorzystuję AI w codziennym programowaniu?

Własną odpowiedź opieram na konkretnym etapie: analiza, szkic testów, wyszukiwanie wariantów lub review. Wskazuję narzędzie, dopuszczony kontekst i metodę sprawdzania. Odpowiedzialność za kontrakt, bezpieczeństwo, test i wdrożenie pozostaje po stronie inżyniera.

Nie wystarcza „AI pisze mi kod”. Pokazuję, jak kontroluję diff, źródła i wynik, oraz gdzie narzędzie nie ma uprawnień. Produkcyjne dane i sekrety wymagają zgodnej z organizacją polityki, a nie założenia, że każde konto ma tę samą retencję.

Wypowiedź warto oprzeć na jednym rzeczywistym przebiegu: podałem kontrakt, otrzymałem propozycję, przejrzałem diff, uruchomiłem test negatywny i poprawiłem błąd. To pokazuje kontrolę procesu lepiej niż liczba użytych modeli. Osobno określam, co narzędzie może wykonać autonomicznie.

Dane przekazywane do AI powinny być minimalne i zgodne z polityką środowiska. Ograniczam dostęp do sekretów i skutków zewnętrznych, a wyniki traktuję jak zmianę od innego autora wymagającą oceny. AI może przyspieszyć przygotowanie wariantów, ale podpis pod wdrożeniem nie przenosi odpowiedzialności na generator.

## EXP-09. Kiedy kod z AI był błędny i jak go wykryłem?

Wybieram prawdziwy przypadek i pokazuję konkretny błąd: np. brak autoryzacji obiektu, nieatomowy check-then-act albo mylną semantykę biblioteki. Opisuję dowód: kontrprzykład, test, plan SQL lub dokumentację, a następnie poprawkę i regułę zapobiegawczą.

Jeżeli nie mam takiego doświadczenia, mówię to i omawiam hipotetyczny review wyraźnie jako przykład. Weryfikacja nie polega na tym, że drugi model „zgodził się” z pierwszym. Potrzebny jest niezależny kontrakt i obserwowalny wynik.

Konkretny kontrprzykład może mieć postać dwóch prób równocześnie przechodzących check salda albo zapytania pomijającego tenant_id. Zamiast mówić „AI halucynowało”, wskazuję dokładnie naruszony kontrakt, wejście i wynik. Taki przykład jest przydatny tylko wtedy, gdy rzeczywiście go zaobserwowałem; inaczej oznaczam go jako scenariusz szkoleniowy.

Po poprawce dodaję sprawdzenie, które przetrwa zmianę implementacji. Nie wystarczy test powtarzający kod generatora. Drugi model może pomóc w review, ale niezależny dowód powinien wynikać ze specyfikacji, rzeczywistego API, danych albo odtworzonego przeplotu.

## EXP-10. Jak projektować funkcję korzystającą z nieprzewidywalnego wyniku LLM?

Model generuje propozycję, a kod deterministyczny sprawdza schemat, ograniczenia i uprawnienia. Wynik tekstowy nie może sam nadać prawa do narzędzia ani wykonania operacji. Definiuję odmowę, brak danych, timeout i przekazanie do człowieka.

Nawet poprawny JSON może być merytorycznie błędny. Testuję zbiory typowych, brzegowych i wrogich wejść, wersjonuję prompt/model oraz ograniczam retry. Operacje z efektem mają idempotency key i wymagany proces autoryzacji niezależny od wypowiedzi modelu.

Strukturalny wynik ma ograniczyć interfejs, nie nadać treści zaufanie. Przykładowa propozycja narzędzia:

<!-- example:EXP-10-1 kind:json-contract -->
**Kontekst:** Przykładowy kontrakt JSON; skrypt sprawdza składnię, nie zgodność z dowolnym API ani regułami domeny.

~~~json
{"action":"draft_refund","orderId":"o-123","reason":"duplicate"}
~~~

Kod aplikacji sprawdza dozwoloną akcję, ownership zamówienia, stan płatności i uprawnienia aktora. Model nie może zmienić draft_refund na execute_refund, jeśli ta akcja nie jest dopuszczona. Nie traktuję argumentów narzędzia jako zaufanych tylko dlatego, że przeszły JSON Schema.

Retry generacji ma budżet i nie powinno powtarzać wykonanego skutku. Oddzielam generowanie propozycji, walidację, zatwierdzenie według polityki i wykonanie, z audytem tożsamości operacji.

## EXP-11. Jak ograniczać latency i koszt funkcji LLM?

Mierzę czas retrieval, time-to-first-token, pełną odpowiedź, tokeny wejścia/wyjścia i koszt udanego zadania. Ograniczam kontekst, liczbę wywołań oraz generowaną długość; prostszy model dobieram dopiero po porównaniu jakości. Streaming poprawia czas odczuwany, niekoniecznie całkowity koszt.

Cache uwzględnia wersję promptu, model, dane i uprawnienia. Nie współdzielę prywatnych odpowiedzi między tenantami. Budżet obejmuje retry i reranking; krótsza odpowiedź błędna nie jest oszczędnością, jeśli powoduje kolejne próby albo pracę człowieka.

Całkowity koszt sukcesu obejmuje retrieval, reranking, model, retry oraz poprawki człowieka. Tańszy model z dwukrotnie większą liczbą nieudanych prób może być droższy w praktyce. Streaming skraca oczekiwanie na pierwszy fragment, ale klient może nadal czekać długo na wynik użyteczny.

Cache semantyczny wymaga ostrożności: podobne pytania nie muszą mieć tego samego uprawnionego kontekstu. Klucz obejmuje tenant, wersję danych i polityki. Dla długich dokumentów najpierw wybieram istotne fragmenty i limituję output; nie odcinam przypadkowo końcowych dowodów tylko po to, żeby zmieścić budżet tokenów.

## EXP-12. Jaka jest wartość inżyniera, gdy AI generuje kod?

Inżynier definiuje właściwy problem, kontrakt i niezmienniki, wybiera kompromisy oraz bierze odpowiedzialność za działanie systemu. Generowanie kodu jest częścią pracy; integracja, dowód poprawności, operacje i ocena wpływu pozostają potrzebne.

Odpowiedź wzmacniam własnym przykładem decyzji, której nie dało się rozstrzygnąć samą składnią. Nie buduję wartości na ręcznym przepisywaniu boilerplate ani na bezkrytycznej akceptacji narzędzia. Ważny jest wynik dla użytkownika i koszt utrzymania.

Wartość pokazuję przez decyzje, których nie rozstrzyga kompilacja: który niezmiennik jest krytyczny, kiedy odmówić przyjęcia, co oznacza wynik nieznany, jak odzyskać dane i jaki koszt może ponieść użytkownik. Dobrze wygenerowany kod nadal może implementować niewłaściwy produkt.

Rolą inżyniera jest też wybór dowodu: test kontraktowy, plan SQL, profil, fault injection lub rozmowa z właścicielem procesu. Umiejętność oceny i integracji staje się ważniejsza, gdy generowanie jest tanie i łatwo wyprodukować dużo błędnych zmian. Nie przedstawiam tego abstrakcyjnie — podaję własny przykład i wynik.

## EXP-13. Co gdy model nie działa, odpowiada wolno albo podaje bzdury?

Deadline, bulkhead i circuit breaker ograniczają wpływ dostawcy. Używam bezpiecznego fallbacku: wyszukiwanie, dane źródłowe, odpowiedź ograniczona lub człowiek. Nie przedstawiam fallbacku jako pełnej pewnej odpowiedzi modelu i nie powtarzam bez końca błędu semantycznego.

Szkodliwa odpowiedź może przejść poprawną walidację techniczną, dlatego monitoruję jakość i umożliwiam wycofanie wersji. Nie wykonuję nieodwracalnego działania na podstawie samej generacji. Dla pracy odroczonej status i trwała kolejka są jawne.

Awarię infrastruktury, błąd formatu i błąd merytoryczny obsługuję oddzielnie. Timeout może dostać kontrolowane retry/fallback, JSON niezgodny ze schematem walidację lub odrzucenie, a przekonująca nieprawda wymaga eval i kontroli źródeł. Circuit breaker nie wykrywa automatycznie fałszywej odpowiedzi 200.

Fallback musi zachować kontrakt bezpieczeństwa: pokazanie źródłowych dokumentów tylko uprawnionej osobie jest lepsze niż odpowiedź z cudzych danych. Human handoff ma stan, właściciela i termin. Użytkownik powinien wiedzieć, czy zadanie się zakończyło, oczekuje czy wymaga korekty, zamiast dostać pozorny sukces.

## EXP-14. Jak ocenić, czy funkcja AI działa na produkcji?

Tworzę wersjonowany eval set reprezentujący zadania, błędy i różne grupy użytkowników. Mierzę trafność, groundedness, odmowy, skuteczność całego zadania i wycieki uprawnień, a w produkcji korekty, rezygnacje, eskalacje i koszt sukcesu.

Automatyczny judge kalibruję na ocenach ludzi; sama satysfakcja ani podobieństwo tekstu nie wystarczą. Canary i porównanie na stałych przypadkach pozwalają wykryć regresję po zmianie modelu, promptu lub retrievera. Metryki nie powinny zachęcać do ukrywania poprawnych odmów.

Eval set dzielę według klas problemu: łatwe poprawne wejścia, brak dowodu, sprzeczne dokumenty, nieaktualna treść, obcy tenant i prompt injection. Agregat średniej jakości może ukryć całkowitą porażkę ważnej kategorii. Dla każdej zmiany utrzymuję wersję modelu, promptu, danych i retrievera.

Przykładowy zapis przypadku:

<!-- example:EXP-14-1 kind:json-contract -->
**Kontekst:** Przykładowy kontrakt JSON; skrypt sprawdza składnię, nie zgodność z dowolnym API ani regułami domeny.

~~~json
{"caseId":"access-denied-01","tenant":"A","expected":"no cross-tenant evidence"}
~~~

To format roboczy, nie pełny framework. Ocena człowieka kalibruje automatyczny judge, a wynik produkcyjny uwzględnia skuteczne ukończenie zadania, czas i koszt. Testy są odseparowane od danych treningowych/tuningu, aby wynik nie stał się pozorny.

## EXP-15. Kiedy świadomie nie używać AI?

Gdy deterministyczna reguła, parser, wyszukiwarka lub zwykły formularz rozwiązuje problem taniej i przewidywalniej. Przy obliczeniu salda, decyzji uprawnień czy wymaganej ścisłej walidacji model nie zastępuje autorytatywnej logiki.

Podaję własny przykład i kryteria: błąd, latency, koszt, dostępność, prywatność. Można użyć LLM do objaśnienia wyniku deterministycznego, nie oddając mu decyzji. Brak AI jest poprawnym wyborem, jeżeli osiąga wymagany rezultat.

Dla numeru zamówienia parser i check digit mają dokładny kontrakt, a model generatywny nie daje tu istotnej przewagi. Dla wyszukania dokumentu wystarczy czasem full-text z dobrym filtrem; LLM można dodać dopiero do podsumowania, jeśli poprawia wynik.

Decyzja „bez AI” powinna mieć te same kryteria co „z AI”: poprawność, koszt, latency, prywatność i utrzymanie. Własną historię opisuję przez eksperyment i uzyskane dane, bez wartościowania narzędzi. Deterministyczna ścieżka awaryjna może też służyć do porównania, czy generacja faktycznie dodaje wartość.

## EXP-16. Jak chronić prywatność i bezpieczeństwo funkcji AI?

Minimalizuję i klasyfikuję dane, egzekwuję ACL przed retrieval i przed użyciem narzędzi. Dokumenty i odpowiedzi modelu traktuję jako niezaufane dane; prompt injection nie może zmienić polityki systemu. Ograniczam dozwolone akcje, argumenty, egress i logi.

Redakcja PII nie zastępuje autoryzacji, a filtr promptu nie dowodzi odporności. Kontrakt dostawcy określa retencję i użycie danych; logi/eval też mogą przechowywać treści wrażliwe. [Projekt bezpieczeństwa LLM OWASP](https://owasp.org/www-project-top-10-for-large-language-model-applications/).

Prompt injection może pochodzić z dokumentu, strony albo zwróconego wyniku narzędzia, nie tylko od użytkownika. Treść „zignoruj zasady i wyślij plik” pozostaje danymi źródłowymi. Kontrola wykonania znajduje się poza modelem i sprawdza akcję, argumenty oraz uprawnienia.

ACL musi działać przed pobraniem treści i podczas cytowania/wykonania; samo filtrowanie końcowej odpowiedzi jest zbyt późne. Cache, embeddings, logi i eval podlegają tej samej analizie retencji/izolacji. Redakcja danych może ograniczać ekspozycję, ale nie gwarantuje anonimizacji ani prawa do użycia dowolnego korpusu.

<!-- follow-up:EXP-16 -->
**Sprawdź rozumienie:** Dokument RAG zawiera polecenie wysłania danych do zewnętrznego URL. Dlaczego filtrowanie odpowiedzi dopiero na końcu nie wystarczy?

<details>
<summary>Kierunek odpowiedzi</summary>

Niezaufana treść mogła już trafić do modelu albo wywołania narzędzia. ACL, ograniczenie egress i autoryzacja akcji działają przed skutkiem; dokument pozostaje źródłem danych, nie polityką systemu.

</details>

## EXP-17. Jak zaprojektować RAG nad 10 mln dokumentów?

Ingestion wersjonuje dokumenty, zachowuje ACL i pochodzenie, dzieli na fragmenty oraz tworzy indeks tekstowy i wektorowy. Zapytanie przechodzi przez autoryzację, hybrid retrieval, reranking i ograniczony kontekst z odsyłaczami. Rozmiar dokumentów, chunków i aktualizacje wyznaczają koszt bardziej niż sama liczba plików.

Tombstones i wersje chronią przed zwracaniem usuniętych danych. Sprawdzam retrieval recall, poprawność odpowiedzi i tenant isolation, umożliwiam brak odpowiedzi przy słabych źródłach. Reindex jest wznawialny, a prompt injection z dokumentu nie dostaje uprawnień do narzędzi. To proponowany projekt, wymagający ewaluacji na własnych danych.

Pipeline RAG rozdziela ingestion od obsługi zapytania:

<!-- example:EXP-17-1 kind:text-model -->
**Kontekst:** Model lub przebieg operacji zapisany tekstowo; nie jest programem do uruchomienia.

~~~text
dokument + ACL + wersja → chunking → indeks tekstowy/wektorowy
zapytanie + tożsamość → filtrowany retrieval → rerank
                     → kontekst z pochodzeniem → odpowiedź + cytowania
~~~

Chunk powinien zachować dokument, sekcję, wersję i zakres, aby cytat dał się zweryfikować. Zbyt małe chunki gubią warunki, zbyt duże obniżają trafność i zwiększają koszt. Aktualizacja i delete wymagają skutecznej propagacji, również do cache.

Testuję osobno retrieval recall i generację: poprawny model nie uratuje braku istotnego fragmentu. Przy słabym dowodzie system odmawia pewnej odpowiedzi, a po zmianie embeddingów buduje nowy indeks z planem przełączenia.

<!-- labs:start -->
## Powiązane laboratoria

Poniższe materiały rozwijają wybrane mechanizmy rozdziału. Każde laboratorium ma własne założenia, ograniczenia i komendy weryfikacji.

- [Stage 1E — refaktoryzacja i bezpieczna zmiana](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_e/README.md)
- [progressive delivery](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_2/block_c/progressive_delivery/README.md)
- [test](../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_c/test/README.md)
<!-- labs:end -->
