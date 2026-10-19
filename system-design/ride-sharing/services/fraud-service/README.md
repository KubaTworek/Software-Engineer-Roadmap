# Fraud Service

## Sens laboratorium

Fraud Service klasyfikuje ryzyko bez przejmowania własności nad cyklem życia przejazdu. Niezmiennik: **brak odpowiedzi fraudu nie może zostać omyłkowo zinterpretowany jako niski risk**, a decyzja musi być audytowalna.

Krytyczny przepływ: zdarzenie domenowe → ekstrakcja stabilnych cech → deterministyczny score → `ALLOW`, `REVIEW` albo `BLOCK` → osobne zdarzenie decyzji. Progi oddzielają mechanizm scoringu od polityki biznesowej.

Rekomendowany test: `FraudServiceTest`.

## Granica produkcyjna

To regułowy baseline, nie model antyfraudowy. Produkcyjnie potrzebne są wersje feature/modelu, store decyzji, ręczny review, explainability, monitoring driftu oraz fail-open/fail-closed dobrany osobno dla zamówienia, płatności i safety.
