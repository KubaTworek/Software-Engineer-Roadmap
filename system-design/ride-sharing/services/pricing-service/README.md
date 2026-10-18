# Pricing Service

## Sens laboratorium

Usługa pokazuje deterministyczną wycenę bazową i surge zależny od relacji popytu do podaży. Niezmiennik: **ta sama wersja reguł i te same wejścia dają tę samą wycenę, a mnożnik pozostaje w dozwolonych granicach**.

Krytyczny przepływ: `cityId` + typ pojazdu + dystans/czas + popyt/podaż → breakdown ceny → ograniczony surge → zaokrąglenie kwoty. Wycena jest snapshotem; produkcyjny klient powinien otrzymać jej identyfikator, wersję i termin ważności.

Rekomendowany test: `PricingServiceTest`, który sprawdza także cap surge przy skrajnym przeciążeniu.

## Granica produkcyjna

Model nie uwzględnia podatków, opłat lokalnych, promocji, minimalnej ceny, supply forecast ani podpisanego quote. Nie należy przeliczać reguł po cichu pomiędzy pokazaniem ceny a utworzeniem ride; właściwym rozwiązaniem jest wersjonowany quote z TTL.
