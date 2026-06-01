# ADR 0001: Use Modular Monolith

## Context

Projekt ma ćwiczyć świadome decyzje architektoniczne, granice modułów, spójność danych, eventy i pracę bliżej produkcji.

## Decision

System jest jednym deployowalnym artefaktem Spring Boot, ale kod jest podzielony na moduły domenowe z własnymi granicami odpowiedzialności.

## Consequences

- Mniejszy koszt operacyjny niż mikroserwisy.
- Łatwiejsze transakcje lokalne.
- Nadal trzeba pilnować zależności między modułami.
- Granice mogą być później podstawą do ekstrakcji wybranych modułów.

## Alternatives considered

- Klasyczny warstwowy monolit.
- Mikroserwisy od początku.
