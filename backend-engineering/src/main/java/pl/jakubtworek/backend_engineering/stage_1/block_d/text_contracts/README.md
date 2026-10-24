# Tekst, Unicode i kontrakt danych

<!-- material-card:start -->
> [!IMPORTANT]
> **Karta materiału**
> - **Zakres:** `fundament`
> - **Uczy:** Tekst, Unicode i kontrakt danych.
> - **Typowy błąd:** Mylenie UTF-16, code points i bajtów oraz inny porządek cursoru niż kolacja SQL.
> - **Najkrótsza weryfikacja:** `.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=TextContractTest" test`
> - **Katalog uruchomienia:** `backend-engineering` (katalog głównego modułu).
> - **Role klas:** `TextContract` = `correct`; test PostgreSQL = `production-boundary`.
> - **Granica:** Czysta Java sprawdza kontrakt reprezentacji; zgodność normalize, collation i keyset potwierdza osobny test rzeczywistego PostgreSQL.
<!-- material-card:end -->

## Problem i niezmiennik

To samo nazwisko może mieć dwie kanonicznie równoważne reprezentacje Unicode.
Java String.length liczy jednostki UTF-16, limit transportu zwykle bajty, a człowiek
widzi klastry grafemów. Porządek String.compareTo nie musi odpowiadać sortowaniu SQL.
Kontrakt musi określić reprezentację, limit i porządek przed zapisaniem danych lub cursoru.

## Kontrprzykład i rozwiązanie

`TextContract` przyjmuje dla nazwy prezentacyjnej NFC, strip i brak znaków sterujących.
Limity code points oraz bajtów UTF-8 są osobne. Encoder/decoder używają REPORT;
domyślne zastąpienie błędnego surrogate znakiem zastępczym mogłoby zmienić tożsamość danych.
NFC zachowuje znak ①; NFKC może zmienić go w 1. Żadna normalizacja nie jest
uniwersalnym zabezpieczeniem przed podobnie wyglądającymi identyfikatorami.

`utf8ByteOrder` porównuje unsigned bytes i odpowiada COLLATE "C" w PostgreSQL UTF-8.
Java porównuje jednostki UTF-16, więc np. U+E000 i emoji mogą mieć inny porządek.
Keyset wymaga tej samej kolacji w WHERE i ORDER BY oraz stabilnego tiebreakera id.
Locale.ROOT usuwa zależność od locale hosta, ale sam lowercase nie definiuje
poprawnej polityki loginu, emaila, hasła ani identyfikatora biznesowego.

## Dowody

Z katalogu backend-engineering:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=TextContractTest" test
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests "-Dtest=PostgreSqlExecutableLabTest" test
```

Pierwszy test rozróżnia UTF-16/code points/UTF-8, błędne wejście, NFC/NFKC,
locale i porządek bajtowy. Drugi wykonuje normalize, distinct i kolejne strony
na prawdziwym PostgreSQL. Nie zastępuje silnika bazy modelem w Javie.

## Ograniczenia i wybór

Code point nie jest grapheme cluster; limit długości widocznej dla użytkownika
wymaga innego kontraktu. Nazwisko nie jest kluczem tożsamości. Nie zmieniaj
automatycznie haseł, local part emaila ani danych podpisanych kryptograficznie.
Zmiana normalizacji/kolacji istniejącego klucza wymaga analizy kolizji, migracji
indeksów i wersjonowania cursoru. Dla sortowania językowego wybierz jawną kolację
i uwzględnij wersję biblioteki kolacji; C jest tu deterministycznym ćwiczeniem.

Źródła: [Unicode normalization](https://www.unicode.org/reports/tr15/),
[String Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html),
[PostgreSQL collation](https://www.postgresql.org/docs/16/collation.html).
