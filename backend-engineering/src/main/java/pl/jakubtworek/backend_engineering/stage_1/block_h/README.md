# Build, zależności i zgodność binarna

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** Build, zależności i zgodność binarna.
> - **Typowy błąd:** Podmiana runtime biblioteki bez sprawdzenia sygnatur już skompilowanego klienta.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=BinaryCompatibilityLabTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** brak jawnej roli w nazwach klas; intencję przykładu określają opis i test, nie sam brak prefiksu.
> - **Granica:** Przykład dowodzi mechanizmu w opisanej granicy; bez testu infrastrukturalnego nie dowodzi zachowania wielu procesów ani konkretnej usługi.
<!-- material-card:end -->

## Problem i niezmiennik

Zielona kompilacja nie gwarantuje zgodnego runtime classpath. Klient skompilowany
z metodą V1 może przestać działać po podmianie biblioteki, nawet gdy nazwa klasy
pozostała taka sama. Niezmiennik: artefakt runtime zawiera kontrakty potrzebne
już skompilowanym klientom.

## Kontrprzykład i rozwiązanie

`BinaryCompatibilityLab` kompiluje bibliotekę V1 i klienta kompilatorem JDK.
Następnie nadpisuje tylko bibliotekę: usunięcie metody wywołuje rzeczywisty
`NoSuchMethodError` w osobnej JVM, zachowanie sygnatury pozwala uruchomić klienta.
Test nie rzuca tego błędu ręcznie. Wyjście procesu ma deadline i jest zapisywane
w katalogu tymczasowym testu.

## Uruchomienie

Z `backend-engineering`:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=BinaryCompatibilityLabTest" test
.\mvnw.cmd --batch-mode --no-transfer-progress dependency:tree
.\mvnw.cmd --batch-mode --no-transfer-progress help:effective-pom
```

W projekcie 2: `gradlew.bat dependencyInsight --dependency temporal-sdk --configuration runtimeClasspath`.
Maven stosuje mediation i dependencyManagement, Gradle własne reguły konfliktu
oraz constraints/platforms. BOM zarządza wersjami, sam nie dodaje bibliotek.
Scope/configuration, zależności pluginów i procesory adnotacji mają odrębne classpathy.
Maven lifecycle uruchamia cele pluginów; Gradle najpierw konfiguruje graf zadań.

## Diagnostyka i aktualizacja

Przy `NoSuchMethodError` ustal klasę faktycznie załadowaną, np. przez
`-Xlog:class+load=info`, oraz porównaj drzewo runtime z kompilacją.
`ClassNotFoundException` zwykle dotyczy jawnego ładowania klasy;
`NoClassDefFoundError` może oznaczać brak zależności albo wcześniejszy błąd
inicjalizacji. Samo dopisanie exclusions nie dowodzi naprawy.

Przypnij wrapper, JDK/toolchain i pluginy; odtwórz build w czystym checkoutcie.
Porównaj sumy JAR i wyjaśnij timestampy, kolejność wpisów i metadane. Nie usuwaj
istniejących lokalnych zmian. Cache przyspiesza build, ale nie dowodzi jego
powtarzalności. Test laboratoryjny nie potwierdza powtarzalności całego repozytorium.

Aktualizację prowadź po jednej granicy: release notes → graf zależności → test
kontraktu → build → uruchomienie → rollback. Zanotuj zgodność źródłową i binarną
osobno. Przykład używa wyłącznie JDK; nie wymaga nowej biblioteki produkcyjnej.
