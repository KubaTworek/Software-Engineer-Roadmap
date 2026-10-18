# Threat model — Marketplace Order Fulfillment

## Chronione aktywa

- pieniądze i możliwość rezerwacji/refundu;
- dostępny stock i niezmiennik `reserved <= available`;
- dane klienta, tokeny i sekrety integracji;
- integralność eventów, webhooków i operacji administracyjnych.

## Trust boundaries

```text
Internet -> HTTP/GraphQL/WebSocket -> application use case -> PostgreSQL
                                      |                    -> outbox
                                      +-> Kafka -> consumer -> partner webhook/payment
CI -> registry -> container runtime -> Kubernetes workload identity
```

Każde przekroczenie granicy wymaga uwierzytelnienia, autoryzacji, limitu rozmiaru,
deadline'u i bezpiecznego logowania. Dane z Kafki są wejściem niezaufanym tak samo
jak request HTTP — broker nie zastępuje walidacji kontraktu.

## Najważniejsze scenariusze i zabezpieczenia

| Zagrożenie | Zabezpieczenie | Negatywny test |
|---|---|---|
| IDOR na zamówieniu | autoryzacja zasobu po customer/tenant, nie tylko rola | klient B nie odczytuje Order klienta A |
| SSRF przez webhook | HTTPS + allowlista + walidacja rozwiązanego IP + brak redirectów | host resolve'uje się do loopback |
| path traversal/upload bomb | generowany klucz, normalized root, limit streamu, magic bytes | `../secret`, rozmiar ponad limit |
| replay webhooka | HMAC nad surowym body, timestamp window i unikalny delivery-id | ponowny delivery-id zostaje odrzucony |
| wyciek sekretu/PII | structured allowlist logging, redakcja, brak payloadów eventów | token nie występuje w logu |
| supply chain | lockfile/checksum, SBOM, skan zależności i pin obrazu po digest | CI blokuje critical CVE |

System ma działać fail-closed: awaria walidatora podpisu lub polityki autoryzacji
nie może skutkować domyślnym zezwoleniem. Sekrety nie należą do Git ani ConfigMap;
manifest oczekuje `marketplace-secret` dostarczonego przez zewnętrzny secret store.
