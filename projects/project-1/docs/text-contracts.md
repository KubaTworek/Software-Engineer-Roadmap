# Kontrakt nazwy klienta

CustomerTextPolicy.name normalizuje nową nazwę osoby do NFC, usuwa zewnętrzne
odstępy, odrzuca controls i błędne surrogate oraz ogranicza ją do 255 code points.
CustomerRegistrationService stosuje tę samą politykę przed ścieżką JDBC;
konstruktor Customer chroni ścieżkę JPA. Zapis nie zależy od domyślnego charsetu hosta.

Naiwne porównanie dwóch zapisów José/Jose+combining accent zwraca false.
Po NFC nowy klient ma jeden format prezentacyjny. To nie jest deduplikacja osób:
dwóch ludzi może nosić tę samą nazwę. Email nie jest automatycznie lowercased ani
normalizowany; zmiana klucza istniejących kont wymaga osobnej polityki i analizy kolizji.
Dotychczasowych nazw nie przepisujemy przy odczycie ani automatyczną migracją.

Weryfikacja z project-1:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress "-Dtest=CustomerTextPolicyTest" test
.\mvnw.cmd --batch-mode --no-transfer-progress -Pinfrastructure-tests verify
```

Laboratorium [kontraktów tekstu](../../../backend-engineering/src/main/java/pl/jakubtworek/backend_engineering/stage_1/block_d/text_contracts/README.md)
pokazuje także limity bajtowe, locale i zgodność sortowania/cursoru z PostgreSQL.
Limit API @Size jest liczbą jednostek String i może być bardziej restrykcyjny
niż limit domenowy dla znaków spoza BMP; nie opisuj go jako liczby widocznych liter.
