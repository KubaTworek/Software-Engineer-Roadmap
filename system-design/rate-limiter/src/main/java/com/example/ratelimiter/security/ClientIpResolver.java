package com.example.ratelimiter.security;

import com.example.ratelimiter.config.RateLimiterProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * ClientIpResolver odpowiada za ustalenie prawdziwego IP klienta.
 *
 * To jest ważne dla rate limitingu per IP.
 *
 * W prostej aplikacji można byłoby użyć:
 *
 * request.getRemoteAddr()
 *
 * Problem pojawia się, gdy aplikacja działa za:
 * - reverse proxy,
 * - load balancerem,
 * - API Gatewayem,
 * - ingress controllerem,
 * - CDN-em.
 *
 * Wtedy request.getRemoteAddr() często wskazuje IP proxy,
 * a nie realnego klienta.
 *
 * Dlatego klasa obsługuje nagłówek X-Forwarded-For,
 * ale ufa mu tylko wtedy, gdy request przyszedł z zaufanego proxy.
 */
@Component
public class ClientIpResolver {

    /**
     * Konfiguracja aplikacji.
     *
     * Z niej pobieramy listę trusted proxies,
     * czyli adresów IP lub zakresów sieci, którym ufamy jako pośrednikom.
     *
     * Przykład:
     *
     * rate-limiter:
     *   security:
     *     trusted-proxies:
     *       - 10.0.0.0/8
     *       - 192.168.0.0/16
     */
    private final RateLimiterProperties properties;

    public ClientIpResolver(RateLimiterProperties properties) {
        this.properties = properties;
    }

    /**
     * Zwraca IP, które powinno być użyte jako client IP w Rate Limiterze.
     *
     * Logika:
     *
     * 1. Pobieramy remoteAddr.
     *    To jest adres bezpośredniego nadawcy requestu.
     *
     * 2. Pobieramy X-Forwarded-For.
     *    Ten nagłówek zawiera łańcuch IP, przez które przechodził request.
     *
     * 3. Jeżeli nie ma X-Forwarded-For albo request nie przyszedł
     *    z trusted proxy, ignorujemy X-Forwarded-For i zwracamy remoteAddr.
     *
     * 4. Jeżeli request przyszedł z trusted proxy, analizujemy chain
     *    z X-Forwarded-For i szukamy pierwszego IP, które nie jest proxy.
     *
     * W rate limitingu jest to kluczowe, bo klient może samodzielnie wysłać
     * fałszywy X-Forwarded-For. Nie wolno ufać temu nagłówkowi,
     * jeśli request nie przyszedł z infrastruktury, którą kontrolujemy.
     */
    public String resolve(HttpServletRequest request) {
        /*
         * remoteAddr to IP bezpośredniego peer'a TCP.
         *
         * Jeśli aplikacja stoi bezpośrednio publicznie,
         * będzie to zwykle IP klienta.
         *
         * Jeśli aplikacja stoi za gatewayem/load balancerem,
         * będzie to IP tego gatewaya/load balancera.
         */
        String remote = request.getRemoteAddr();

        /*
         * X-Forwarded-For może wyglądać np. tak:
         *
         * X-Forwarded-For: 203.0.113.10, 10.0.1.5, 10.0.2.7
         *
         * Zwykle pierwszy adres to oryginalny klient,
         * a kolejne to proxy po drodze.
         *
         * Ale ten nagłówek jest łatwy do sfałszowania przez klienta,
         * więc nie można mu ufać bezwarunkowo.
         */
        String xff = request.getHeader("X-Forwarded-For");

        /*
         * Jeśli nie ma X-Forwarded-For, nie mamy czego analizować.
         *
         * Jeśli request nie przyszedł z trusted proxy,
         * to znaczy, że klient mógł sam dopisać dowolny X-Forwarded-For.
         * Wtedy ignorujemy ten nagłówek i używamy remoteAddr.
         */
        if (xff == null || xff.isBlank() || !isTrustedProxy(remote)) {
            return remote;
        }

        /*
         * Rozbijamy X-Forwarded-For na listę adresów IP.
         *
         * Usuwamy spacje i puste elementy, bo nagłówek może zawierać:
         *
         * "203.0.113.10, 10.0.1.5"
         */
        List<String> chain = Arrays.stream(xff.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        /*
         * Szukamy pierwszego IP w łańcuchu, które NIE jest trusted proxy.
         *
         * To jest kandydat na prawdziwego klienta.
         *
         * Przykład:
         *
         * XFF: 203.0.113.10, 10.0.1.5, 10.0.2.7
         *
         * Jeśli 10.0.0.0/8 jest trusted proxy,
         * to 203.0.113.10 zostanie zwrócone jako client IP.
         */
        for (String ip : chain) {
            if (!isTrustedProxy(ip)) {
                return ip;
            }
        }

        /*
         * Jeśli wszystkie IP w X-Forwarded-For są trusted proxies,
         * nie znaleźliśmy oczywistego klienta.
         *
         * Wtedy zwracamy pierwszy element z chain, jeśli istnieje,
         * a jeśli chain jest pusty, wracamy do remoteAddr.
         *
         * To jest fallback dla nietypowych konfiguracji proxy.
         */
        return chain.isEmpty() ? remote : chain.getFirst();
    }

    /**
     * Sprawdza, czy dany adres IP należy do zaufanych proxy.
     *
     * Ta metoda obsługuje:
     * - dokładne dopasowanie IP,
     * - kilka uproszczonych zakresów prywatnych CIDR.
     *
     * Uwaga:
     * to nie jest pełna implementacja CIDR.
     * Dla projektu edukacyjnego jest wystarczająca,
     * ale w produkcji lepiej użyć pełnego parsera CIDR.
     */
    private boolean isTrustedProxy(String ip) {
        if (ip == null) {
            return false;
        }

        for (String trusted : properties.getSecurity().getTrustedProxies()) {
            /*
             * Dokładne dopasowanie IP.
             *
             * Przykład:
             * trusted = "10.0.1.5"
             * ip      = "10.0.1.5"
             */
            if (trusted.equals(ip)) {
                return true;
            }

            /*
             * Uproszczona obsługa zakresu 10.0.0.0/8.
             *
             * Wszystkie adresy zaczynające się od "10."
             * traktujemy jako zaufane, jeśli taki zakres jest w konfiguracji.
             */
            if (trusted.equals("10.0.0.0/8") && ip.startsWith("10.")) {
                return true;
            }

            /*
             * Uproszczona obsługa zakresu 192.168.0.0/16.
             */
            if (trusted.equals("192.168.0.0/16") && ip.startsWith("192.168.")) {
                return true;
            }

            /*
             * Uproszczona obsługa zakresu 172.16.0.0/12.
             *
             * Zakres prywatny obejmuje:
             * 172.16.x.x - 172.31.x.x
             */
            if (trusted.equals("172.16.0.0/12")
                    && ip.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
                return true;
            }
        }

        return false;
    }
}