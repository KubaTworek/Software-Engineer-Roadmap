# Wzorce bezpieczeństwa aplikacji

[Moduł](../../README.md) · [Indeks](../../INDEKS.md) · [Materiały](../../MATERIALY.md#security)

Wzorce granic zaufania i dostępu. **8 kart.**

**Status przykładów:** moduł dokumentacyjny. Fragmenty Java, SQL i HTTP oraz pseudokod są objaśnieniami
mechanizmu; kompletne modele i scenariusze poprawności również znajdują się w Markdown. Nie ma osobnej
aplikacji, buildu Java ani testów uruchamianych przez Maven.

**Rozbudowany kod i omówienie:** [izolacja tenantów w odczycie i zapisie](../../labs/04-api.md). Laboratorium
realizuje wskazany podzbiór wzorców tej kategorii.

**Jak rozumieć tę kategorię:** Bezpieczeństwo wymaga nazwania podmiotu, zasobu, akcji i granicy zaufania.
Uwierzytelnienie odpowiada, kto się przedstawia, autoryzacja co wolno mu zrobić, a szyfrowanie i audyt mają
inne cele. Żaden pojedynczy komponent nie zastępuje pozostałych. Szkice pokazują miejsca decyzji, nie
kompletne implementacje kryptografii, walidacji tokenów ani konfiguracji produkcyjnej.

## Spis kart

- [Reference Monitor / Policy Enforcement Point](#reference-monitor--policy-enforcement-point)
- [RBAC / ABAC / Policy Decision Point](#rbac--abac--policy-decision-point)
- [Capability / Valet Key / Signed URL](#capability--valet-key--signed-url)
- [Token Relay / Token Exchange / On-behalf-of](#token-relay--token-exchange--on-behalf-of)
- [Workload Identity / mTLS / Short-lived Credentials](#workload-identity--mtls--short-lived-credentials)
- [Secrets Broker / Rotation / Envelope Encryption](#secrets-broker--rotation--envelope-encryption)
- [Trust Boundary Validation / Allowlist / Safe Parsing](#trust-boundary-validation--allowlist--safe-parsing)
- [Append-only Audit Trail / Tamper-evident Log](#append-only-audit-trail--tamper-evident-log)

## Reference Monitor / Policy Enforcement Point

**Intuicja:** Ukryty przycisk w UI nie zabrania wywołania API bezpośrednio. Reference Monitor wymaga, aby
każda chroniona operacja przeszła przez zaufany, niepomijalny punkt decyzji. Policy Enforcement Point
egzekwuje wynik dla konkretnego zasobu, także gdy wejście przyszło z innego adaptera niż zwykły ekran.

**Problem i mechanizm:** Każda chroniona operacja przechodzi przez niepomijalny punkt egzekwowania decyzji
dostępu.

**Role i współpraca:** Principal identyfikuje podmiot, resource zakres danych, policy decyzję, enforcement
blokuje wykonanie. Po sprawdzeniu nie może istnieć boczna niechroniona droga do tej samej operacji.

**Przykład:** GET /orders/42 sprawdza principal, tenant i właściciela zasobu w usłudze, nie tylko w gateway.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Order order = orders.loadWithinTenant(principal.tenantId(), orderId);
policy.requireAllowed(principal, Action.READ, order);
return toResponse(order);
// Principal pochodzi z zaufanej warstwy uwierzytelnienia.
```

**Konkretny przypadek użycia:** Sprawdzanie dostępu do faktury

- **Wejście:** Tenant A prosi o odczyt faktury należącej do tenant B.
- **Przebieg:** Każda ścieżka dostępu przechodzi przez punkt egzekwowania, który ocenia użytkownika i zasób.
- **Oczekiwany wynik:** Odczyt jest odrzucony, także przez alternatywny endpoint pobierania PDF.
- **Błąd lub granica:** Sprawdzenie tylko w interfejsie użytkownika nie chroni API; pominięta ścieżka omija
  politykę.

**Kiedy zastosować:** Dla każdej operacji wymagającej dostępu. Sprawdzenie samej roli w gateway nie zastępuje
kontroli właściciela konkretnego orderId.

**Koszt i pułapka:** Zabezpieczenie ścieżki UI nie chroni bezpośredniego wywołania API; cache decyzji wymaga
polityki unieważnienia.

**Rozwinięcie i ślad wykonania:** Decyzja dostępu musi obejmować podmiot, akcję i konkretny zasób po ustaleniu
scope. ConditionalDocuments używa klucza tenant–id i nie ujawnia, czy obiekt istnieje u kogoś innego. To
lokalny model izolacji; uwierzytelnienie i zaufane pochodzenie Scope pozostają obowiązkiem adaptera
wejściowego.

**Ćwiczenie sprawdzające:** Spróbuj odczytać ID innego tenanta przez poprawnie uwierzytelnionego użytkownika.

## RBAC / ABAC / Policy Decision Point

**Intuicja:** Rola „support” opisuje rodzaj pracy, lecz niekoniecznie prawo do danych każdego klienta. RBAC
wiąże akcje z rolami, ABAC ocenia atrybuty podmiotu, zasobu i kontekstu. Policy Decision Point może
centralizować tę ocenę; nadal ktoś musi zastosować decyzję przy operacji.

**Problem i mechanizm:** Decyzja wynika z ról albo atrybutów podmiotu, zasobu, akcji i kontekstu.

**Role i współpraca:** Role i atrybuty są zaufanymi wejściami, PDP wydaje decyzję, PEP jej przestrzega. Reguła
deny i awaria źródła mają ustalony wynik.

**Przykład:** Agent support ma rolę, ale odczytuje tylko tenanty przypisane w atrybutach kontraktu.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
boolean allowed = principal.roles().contains("support")
        && principal.assignedTenants().contains(order.tenantId())
        && action == Action.READ;
if (!allowed) throw new AccessDenied();
```

**Konkretny przypadek użycia:** Zatwierdzenie kosztownej refundacji

- **Wejście:** Operator ma rolę support, kwota wynosi 9000, limit samodzielny 1000.
- **Przebieg:** PDP łączy rolę z atrybutami kwoty i zakresu; PEP wykonuje decyzję przed zmianą.
- **Oczekiwany wynik:** Refundacja wymaga dodatkowego uprawnienia albo zatwierdzenia.
- **Błąd lub granica:** Nieświeże atrybuty mogą dać błędną decyzję; domyślna ścieżka błędu nie może przyznawać
  dostępu.

**Kiedy zastosować:** Gdy polityka ma być spójna i testowalna. Nie traktuj samego posiadania roli jako zgody
na każdy tenant i dowolny zasób.

**Koszt i pułapka:** Rola admin nie powinna automatycznie znosić granic tenantów; centralna polityka może stać
się zależnością dostępności.

**Rozwinięcie i ślad wykonania:** Rola może dopuścić klasę działań, a atrybuty ograniczyć zakres rekordów.
Zmiana przypisania tenanta wymaga uwzględnienia cache decyzji. Testy powinny obejmować odmowę mimo poprawnej
roli oraz awarię źródła polityki, bo sukces uwierzytelnienia nie daje prawa do każdej operacji.

**Ćwiczenie sprawdzające:** Zbuduj macierz allow/deny i ustal fail-closed przy braku polityki.

## Capability / Valet Key / Signed URL

**Intuicja:** Czasem klient powinien wysłać jeden plik bez otrzymania ogólnych credentiali do storage.
Capability reprezentuje ograniczone prawo w posiadanym tokenie. Valet Key lub Signed URL może zawęzić zasób,
metodę i termin, tak aby odbiorca wykonał tylko uzgodnioną czynność.

**Problem i mechanizm:** Ograniczone prawo do konkretnej operacji jest reprezentowane krótkotrwałym,
kontrolowanym tokenem.

**Role i współpraca:** Zaufany issuer wydaje prawo, klient je przenosi, storage weryfikuje zakres. Wykradziony
token nadal daje uprawnienie w swoim zakresie, więc krótki czas i bezpieczne przekazanie są istotne.

**Przykład:** Signed URL dopuszcza PUT do jednego objectId przez 5 minut z ograniczeniem typu i rozmiaru.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
UploadGrant grant = storageSigner.issueUpload(
        objectId,
        Duration.ofMinutes(5),
        maxBytes,
        "image/png");
return grant.clientUrl();
```

**Konkretny przypadek użycia:** Jednorazowy zakres pobrania raportu

- **Wejście:** Klient może pobrać tylko raport R, metodą GET, przez 60 sekund.
- **Przebieg:** Serwis wystawia podpisany URL o wąskim zakresie, magazyn weryfikuje podpis i termin.
- **Oczekiwany wynik:** Posiadacz linku pobiera R bez uzyskania ogólnego konta do magazynu.
- **Błąd lub granica:** Link może być przekazany dalej i użyty wielokrotnie do wygaśnięcia, jeśli nie dodano
  osobnego mechanizmu jednokrotności.

**Kiedy zastosować:** Dla delegowania wąskich operacji. Link nie powinien pozwalać zmienić objectId, metody
lub limitów bez naruszenia walidacji.

**Koszt i pułapka:** Posiadacz linku może wykorzystać prawo w jego zakresie; logi i referer mogą ujawnić
token. Odwołanie bywa trudne.

**Rozwinięcie i ślad wykonania:** Prawo powinno obejmować konkretny zasób, metodę i termin zamiast ogólnego
dostępu do całego store. Odbiorca nie może rozszerzyć objectId ani zmienić PUT na GET bez naruszenia
walidacji. Przejęty token nadal daje prawa w jego zakresie, więc retencja i ograniczenie wycieku mają
znaczenie.

**Ćwiczenie sprawdzające:** Sprawdź zmianę objectId, metodę GET zamiast PUT i wygaśnięcie tokenu.

## Token Relay / Token Exchange / On-behalf-of

**Intuicja:** Usługa pośrednia może działać w imieniu użytkownika, ale jego token nie musi być przeznaczony
dla każdej kolejnej usługi. Token Relay przekazuje go tylko zgodnie z kontraktem, Token Exchange może wydać
token dla innej audiencji i węższego zakresu. Samo skopiowanie nagłówka nie uzgadnia uprawnień.

**Problem i mechanizm:** Usługa propaguje albo wymienia uprawnienie użytkownika na token o właściwej audiencji
i zakresie.

**Role i współpraca:** Klient ma tożsamość użytkownika, pośrednik własną tożsamość, issuer wydaje właściwy
token. Docelowa usługa waliduje go dla siebie i nadal autoryzuje zasób.

**Przykład:** BFF wymienia token klienta na token do Orders z minimalnym scope zamiast przekazywać go do
każdej usługi.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Token delegated = tokenService.exchange(
        userToken, "orders-api", Set.of("orders:read"));
return ordersClient.get(orderId, delegated);
// Docelowe Orders ponownie waliduje issuer, audience i uprawnienie.
```

**Konkretny przypadek użycia:** Wywołanie faktur w imieniu klienta

- **Wejście:** Frontend ma token dla API zamówień, serwis potrzebuje API faktur.
- **Przebieg:** Serwis wymienia token na token z właściwym audience i ograniczonym zakresem.
- **Oczekiwany wynik:** API faktur może odróżnić podmiot i delegującego oraz sprawdzić swój kontrakt.
- **Błąd lub granica:** Przekazanie oryginalnego tokenu do dowolnej usługi rozszerza ryzyko i może naruszyć
  ograniczenie audience.

**Kiedy zastosować:** Dla delegacji między usługami z określonym modelem zaufania. Nie używaj jednego
szerokiego tokenu do wszystkich downstreamów bez analizy audiencji i scope.

**Koszt i pułapka:** Nie wolno zakładać, że token dla jednej audiencji działa dla innej; logi nie mogą
zawierać tokenów.

**Rozwinięcie i ślad wykonania:** Każdy hop weryfikuje token dla własnej audiencji zamiast ufać, że zrobił to
poprzedni serwer. Wymiana może zawęzić zakres i okres ważności. Oddziel tożsamość użytkownika od tożsamości
usługi wykonującej połączenie, bo obie mogą być potrzebne do prawidłowej decyzji.

**Ćwiczenie sprawdzające:** Wskaż granice walidacji issuer, audience i scope na każdym hopie.

## Workload Identity / mTLS / Short-lived Credentials

**Intuicja:** Współdzielone stałe hasło utrudnia rozpoznanie wykonawcy i odwołanie dostępu. Workload Identity
nadaje tożsamość działającej usłudze, a krótkotrwałe credentiale ograniczają czas ważności. mTLS może
uwierzytelnić peer, lecz nie mówi jeszcze, czy wolno mu wykonać daną operację biznesową.

**Problem i mechanizm:** Proces otrzymuje krótkotrwałą tożsamość zamiast współdzielonego hasła w pliku.

**Role i współpraca:** Platforma wydaje tożsamość, workload ją przedstawia, odbiorca waliduje i stosuje
politykę. Odnowienie oraz rotacja mają własny lifecycle i zależności.

**Przykład:** Usługa używa certyfikatu workload i roli do zapisu tylko we własnym bucketcie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
Credential credential = workloadIdentity.forAudience("storage");
storageClient.authorize(credential);
storageClient.write(ownedBucket, objectId, bytes);
// Zakres roli ogranicza zapis do zasobów tej usługi.
```

**Konkretny przypadek użycia:** Tożsamość workera bez stałego hasła

- **Wejście:** Worker na platformie potrzebuje dostępu do kolejki przez 10 minut.
- **Przebieg:** Platforma poświadcza workload, wystawia krótki credential, klient odnawia go przed końcem.
- **Oczekiwany wynik:** Dostęp wynika z tożsamości uruchomionego workera i ograniczonej polityki.
- **Błąd lub granica:** mTLS potwierdza tożsamość kanału, ale autoryzacja konkretnej operacji nadal wymaga
  polityki.

**Kiedy zastosować:** Dla usług działających w kontrolowanym środowisku. Zaplanuj awarię odnowienia i okres
współistnienia kluczy, zamiast tylko udanego pierwszego startu.

**Koszt i pułapka:** mTLS identyfikuje peer, ale nie autoryzuje automatycznie każdej operacji. Rotacja i
awaria wystawcy wymagają planu.

**Rozwinięcie i ślad wykonania:** Krótko żyjące credentiale zmniejszają okno nadużycia, ale ich odnawianie
staje się zależnością dostępności. Weryfikuj tożsamość peer i osobno uprawnienie do zasobu. Przy rotacji
testuj okres współistnienia starego oraz nowego klucza, nie tylko wydanie nowego certyfikatu.

**Ćwiczenie sprawdzające:** Zaprojektuj rotację certyfikatu oraz odrzucenie starej tożsamości.

## Secrets Broker / Rotation / Envelope Encryption

**Intuicja:** Klucz szyfrujący wszystkie dane nie powinien podróżować razem z nimi jako jawna konfiguracja.
Envelope Encryption używa klucza danych do payloadu i klucza nadrzędnego do ochrony tego pierwszego. Secrets
Broker kontroluje dostarczenie potrzebnego sekretu, a rotacja zmienia go według określonego kontraktu.

**Problem i mechanizm:** Sekrety i klucze są dostarczane przez kontrolowany kanał, a klucz danych jest
chroniony kluczem nadrzędnym.

**Role i współpraca:** DEK szyfruje dane, KEK chroni DEK, KMS lub broker egzekwuje prawo użycia. Kopie
odszyfrowanych danych i logi pozostają osobną granicą ochrony.

**Przykład:** Plik szyfruje losowy DEK; KMS chroni DEK, a aplikacja nie przechowuje głównego klucza w
repozytorium.

**Mechanizm w kodzie:** Pseudokod: nazwy oznaczają role i operacje z opisu. To schemat algorytmu lub
interakcji, bez deklaracji implementacji infrastruktury.

```text
DEK <- generator klucza danych
ciphertext <- uznane API szyfrowania uwierzytelnionego(payload, DEK)
wrappedDEK <- KMS.wrap(DEK)
zapisz ciphertext + wymagane metadane + wrappedDEK
odczyt wymaga prawa do KMS.unwrap
```

**Konkretny przypadek użycia:** Rotacja klucza szyfrującego dokumenty

- **Wejście:** Każdy dokument ma własny klucz danych, opakowany kluczem głównym K1.
- **Przebieg:** Broker kontroluje wydawanie, nowy klucz K2 opakowuje klucze danych zgodnie z planem rotacji.
- **Oczekiwany wynik:** Nowe operacje używają K2 bez ujawniania klucza głównego aplikacji.
- **Błąd lub granica:** Wycofanie K1 przed przepakowaniem wszystkich kluczy danych może odciąć dostęp do
  starszych dokumentów.

**Kiedy zastosować:** Dla kontrolowanego zarządzania sekretami i szyfrowaniem. Nie implementuj własnych
prymitywów; sposób użycia uznanego API musi obejmować jego wymagania integralności i nonce.

**Koszt i pułapka:** Szyfrowanie nie zastępuje dostępu, backupu kluczy i kontroli logów; rotacja master key i
danych to inne operacje.

**Rozwinięcie i ślad wykonania:** Klucz danych szyfruje payload, a klucz nadrzędny chroni klucz danych.
Pozwala to zmieniać ochronę DEK bez automatycznego przepisywania całego pliku. Pozbawienie usługi uprawnienia
decrypt nie usuwa kopii odszyfrowanej już w pamięci ani wcześniej ujawnionych logów.

**Ćwiczenie sprawdzające:** Opisz dostęp do danych po odebraniu prawa decrypt oraz strategię re-encryption.

## Trust Boundary Validation / Allowlist / Safe Parsing

**Intuicja:** Nie każde dane z poprawnym JSON są bezpieczne do użycia. Trust Boundary Validation sprawdza
kształt, rozmiar i znaczenie, zanim wejście uruchomi kosztowną albo uprzywilejowaną operację. Walidacja musi
być dobrana do rzeczywistego zasobu: URL wymaga innej analizy niż liczba sztuk.

**Problem i mechanizm:** Dane są walidowane przy wejściu do granicy zaufania przed wykonaniem kosztownych lub
uprzywilejowanych działań.

**Role i współpraca:** Adapter wejścia stosuje limity, parser buduje strukturę, walidator semantyczny odrzuca
niewłaściwe wartości. Autoryzacja nadal sprawdza prawo do wskazanego zasobu.

**Przykład:** Importer dopuszcza tylko znane typy i limity rozmiaru, a downloader nie podąża za dowolnym
wewnętrznym URL.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
limits.requireBodyWithin(maxBodyBytes, request);
UploadCommand command = parser.parseStrictly(request.body());
allowedTypes.require(command.contentType());
limits.requireFileWithin(maxFileBytes, command.size());
policy.requireUploadAllowed(principal, command.target());
```

**Konkretny przypadek użycia:** Import adresu do pobrania pliku

- **Wejście:** Użytkownik przekazuje URL, który aplikacja ma odczytać.
- **Przebieg:** Walidacja dopuszcza wymagany schemat i cele, kontroluje przekierowania, rozmiar i format
  danych.
- **Oczekiwany wynik:** Import odrzuca cel spoza polityki i nadmierny payload przed kosztownym przetwarzaniem.
- **Błąd lub granica:** Walidacja samego napisu hosta nie chroni przed zmianą DNS; egzekwowanie musi
  uwzględniać rzeczywisty cel połączenia.

**Kiedy zastosować:** Na każdej granicy niezaufanych danych. Allowlista jest konkretną regułą dopuszczenia,
nie ogólną obietnicą, że dowolny tekst został „oczyszczony”.

**Koszt i pułapka:** Walidacja syntaktyczna nie zastępuje autoryzacji; filtrowanie tylko tekstu hosta może nie
chronić przed SSRF i redirectem.

**Rozwinięcie i ślad wykonania:** Waliduj limit kosztu przed kosztownym parsowaniem, a semantykę po odczytaniu
struktury. Dla downloadera sprawdzaj również redirect i rzeczywisty cel połączenia, nie tylko początkowy tekst
URL. Autoryzacja do istniejącego zasobu jest osobną kontrolą, której poprawny format wejścia nie zastępuje.

**Ćwiczenie sprawdzające:** Zaprojektuj test z redirectem do adresu wewnętrznego i nadmiernym payloadem.

## Append-only Audit Trail / Tamper-evident Log

**Intuicja:** Po zmianie uprawnień trzeba móc ustalić, kto ją zlecił, co zmienił i jaki był wynik. Audit Trail
zachowuje taki ślad w kontrolowanym zakresie. Append-only utrudnia przepisywanie, a tamper-evident mechanizmy
pomagają wykrywać zmiany; nie dowodzą automatycznie kompletności całej historii.

**Problem i mechanizm:** Istotna operacja ma trwały ślad kto, co, kiedy i z jakim wynikiem, chroniony przed
nieuprawnioną zmianą.

**Role i współpraca:** Zdarzenie audytu ma podmiot, cel, akcję i wynik. Osobna polityka reguluje zapis, odczyt
i retencję. Korekta powinna dopisać nowy fakt, a nie ukryć stary.

**Przykład:** Zmiana uprawnień zapisuje actorId, targetId i decisionId w odrębnym kontrolowanym audycie.

**Mechanizm w kodzie:** Szkic Java: pokazuje współpracę i kolejność operacji. Nazwy portów, obiektów i metod
domenowych opisano powyżej; fragment wymaga ich implementacji oraz otoczenia aplikacji i nie jest osobnym
programem.

```java
audit.append(new PermissionChanged(
        actor.id(), target.id(), oldRole, newRole,
        decision.id(), clock.instant()));
// Zapis audytu ma odrębny, wymagany kontrakt trwałości i dostępu.
```

**Konkretny przypadek użycia:** Ślad zatwierdzenia refundacji

- **Wejście:** Operator zatwierdza refundację order42; potrzebne są osoba, czas, decyzja i korelacja.
- **Przebieg:** Zdarzenie audytowe trafia do kontrolowanego append-only logu z ochroną integralności.
- **Oczekiwany wynik:** Późniejszy przegląd pozwala odtworzyć kto zatwierdził zmianę i wykryć modyfikację
  wpisów.
- **Błąd lub granica:** Łańcuch hashy bez zewnętrznego zakotwiczenia nie zapobiega przepisaniu całej historii
  przez uprzywilejowanego sprawcę.

**Kiedy zastosować:** Dla działań o istotnym znaczeniu i rozliczalności. Zwykły log aplikacji bez ochrony oraz
retencji nie musi spełniać wymaganego kontraktu audytu.

**Koszt i pułapka:** Zwykły log tekstowy może być usunięty; hash chain pomaga wykrywać zmiany, ale nie dowodzi
kompletności bez zakotwiczenia.

**Rozwinięcie i ślad wykonania:** Audyt ma być możliwy do powiązania z decyzją i chroniony przed zmianą przez
zwykłego użytkownika. Hash chain może ujawnić zmianę środka, ale bez niezależnego punktu odniesienia nie
dowodzi, że nie obcięto końca. Ustal retencję, dostęp i sposób korekty błędnego faktu bez przepisywania
historii.

**Ćwiczenie sprawdzające:** Zmień środkowy rekord i opisz wykrycie oraz ryzyko obcięcia końca historii.
