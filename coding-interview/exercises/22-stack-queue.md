# Zadania: stos i kolejka

[Bank zadań i uruchamianie](README.md) · [Karta techniki](../patterns/22-stack-queue.md)

Każde zadanie jest niezależne; metody pozostają TODO. Importy java.util.* zapewnia skrypt.

## SQ-01 — poprawne nawiasy

**Poziom:** L1
**Wymagania:** [stos i kolejka](../patterns/22-stack-queue.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Napis zawiera tylko ()[]{}. Sprawdź, czy nawiasy są poprawnie zagnieżdżone i domknięte. Pusty napis daje true. Długość do 100 000, O(n) czasu i O(n) pamięci.

<details>
<summary>Wskazówka 1</summary>

Zamknięcie musi odpowiadać ostatniemu niedomkniętemu otwarciu.

</details>

<details>
<summary>Wskazówka 2</summary>

Odkładaj otwarcia na stos, zamknięcie porównuj ze szczytem.

</details>

<details>
<summary>Wskazówka 3</summary>

Pusty stos przed zamknięciem i niepusty stos po końcu oznaczają błąd.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:SQ-01 -->
```java
static boolean validBrackets(String text) {
    throw new UnsupportedOperationException("TODO SQ-01");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:SQ-01 -->
```java
static void verify() {
    assert validBrackets("([]{})"); assert validBrackets("");
    assert !validBrackets("([)]"); assert !validBrackets("]");
    assert !validBrackets("(()"); assert validBrackets("()[]{}");
}
```

## SQ-02 — oblicz wyrażenie RPN

**Poziom:** L2
**Wymagania:** [stos i kolejka](../patterns/22-stack-queue.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Tokeny to liczby int lub +,-,*,/. Wyrażenie RPN jest poprawne, dzielenie nie jest przez zero, każde działanie mieści się w int. Dzielenie obcina do zera. Zwróć wynik, bez mutacji tokenów. Do 100 000 tokenów, O(n) czasu i O(n) pamięci.

<details>
<summary>Wskazówka 1</summary>

Operator zużywa dwie ostatnie gotowe wartości.

</details>

<details>
<summary>Wskazówka 2</summary>

Pierwszy zdjęty operand jest prawy; kolejny lewy.

</details>

<details>
<summary>Wskazówka 3</summary>

Po obliczeniu działania odkładaj wynik; na końcu zostaje jedna wartość.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:SQ-02 -->
```java
static int evaluateRpn(String[] tokens) {
    throw new UnsupportedOperationException("TODO SQ-02");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:SQ-02 -->
```java
static void verify() {
    assert evaluateRpn(new String[] {"2","1","+","3","*"})==9;
    assert evaluateRpn(new String[] {"3","2","-"})==1;
    assert evaluateRpn(new String[] {"-7","3","/"})==-2;
    assert evaluateRpn(new String[] {"4","13","5","/","+"})==6;
    assert evaluateRpn(new String[] {"-8"})==-8;
}
```

## SQ-03 — kolejka przez dwa stosy

**Poziom:** L2
**Wymagania:** [stos i kolejka](../patterns/22-stack-queue.md), [Java i koszty](../JAVA.md).

**Treść i kontrakt.** Obsłuż równoległe tablice ops i args. add dopisuje args[i], remove usuwa najstarszy i go zwraca, peek zwraca najstarszy bez usuwania. Wynik int[] zawiera odczyty w kolejności operacji. W args dla remove/peek wartość ignoruj. Nigdy nie odczytujemy pustej kolejki; liczba operacji do 100 000. Użyj wyłącznie dwóch stosów do przechowywania elementów kolejki. Docelowo amortyzowane O(1) na operację, O(n) pamięci.

<details>
<summary>Wskazówka 1</summary>

Nowo dodany element nie może wyprzedzić starszych już gotowych do odczytu.

</details>

<details>
<summary>Wskazówka 2</summary>

Rozdziel stos nowych elementów i stos do odczytu.

</details>

<details>
<summary>Wskazówka 3</summary>

Odwracaj nowy stos do odczytowego wyłącznie, gdy odczytowy jest pusty.

</details>

Kontekst: typy wejścia i nierozwiązany szkielet.

<!-- exercise:SQ-03 -->
```java
static int[] queueScript(String[] ops, int[] args) {
    throw new UnsupportedOperationException("TODO SQ-03");
}
```

Kontekst: wykonywalne testy kontraktu.

<!-- tests:SQ-03 -->
```java
static void verify() {
    assert Arrays.equals(queueScript(new String[] {"add","add","remove","add","peek","remove","remove"}, new int[] {1,2,0,3,0,0,0}),new int[] {1,2,2,3});
    assert queueScript(new String[0],new int[0]).length==0;
    assert Arrays.equals(queueScript(new String[] {"add","peek","peek","remove"},new int[] {-1,0,0,0}),new int[] {-1,-1,-1});
    assert queueScript(new String[] {"add"},new int[] {5}).length==0;
}
```
