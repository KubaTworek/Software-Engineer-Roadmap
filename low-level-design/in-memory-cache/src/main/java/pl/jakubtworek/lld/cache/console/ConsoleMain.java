package pl.jakubtworek.lld.cache.console;

import pl.jakubtworek.lld.cache.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Scanner;

/**
 * Tekstowy adapter cache<String,String>; generyczność API badają także testy innych
 * typów. Advance porusza Ticker, wall koryguje Clock. Żadne nie sprząta cache.
 * Nowa pojemność tworzy nową instancję dopiero po poprawnej konstrukcji.
 * Klucze bez spacji; wartość to reszta linii. Strumienie należą do wywołującego.
 */
public final class ConsoleMain {

    private ConsoleMain() {
    }

    public static void main(String[] args) {
        run(new InputStreamReader(System.in, StandardCharsets.UTF_8), new PrintWriter(System.out, true,
                StandardCharsets.UTF_8));
    }

    public static void run(Readable source, PrintWriter out) {
        var input = new Scanner(source);
        var clock = new ManualClock(Instant.parse("2026-10-23T12:00:00Z"));
        var ticker = new ManualTicker();
        EvictionPolicy policy = EvictionPolicy.LRU;
        Cache<String, String> cache = new InMemoryCache<>(3, policy, ticker, clock);
        out.println("Cache w pamięci — projekt edukacyjny. Pojemność 3. help, q.");
        while (input.hasNextLine()) {
            String line = input.nextLine().stripLeading();
            if (line.isBlank())
                continue;
            if (line.equals("q"))
                break;
            String[] parts = line.split("\\s+", 2);
            String name = parts[0];
            String data = parts.length == 2 ? parts[1] : "";
            try {
                switch(name) {
                    case "put" ->
                        {
                            String[] fields = data.split("\\s+", 2);
                            if (fields.length != 2)
                                throw new IllegalArgumentException("put KEY VALUE");
                            cache.put(fields[0], fields[1]);
                            out.println("OK");
                        }
                    case "put-ttl" ->
                        {
                            String[] fields = data.split("\\s+", 3);
                            if (fields.length != 3)
                                throw new IllegalArgumentException("put-ttl KEY SECONDS VALUE");
                            Duration ttl = Duration.ofSeconds(Long.parseLong(fields[1]));
                            cache.put(fields[0], fields[2], ttl);
                            out.println("OK");
                        }
                    case "get", "peek", "remove" ->
                        {
                            String key = single(data);
                            var result = switch(name) {
                                case "get" ->
                                    cache.get(key);
                                case "peek" ->
                                    cache.peek(key);
                                default ->
                                    cache.invalidate(key);
                            };
                            out.println(result.map(value -> "HIT " + value).orElse("MISS"));
                        }
                    case "advance" ->
                        {
                            ticker.advance(Duration.ofSeconds(Long.parseLong(single(data))));
                            out.println("Czas: " + clock.instant() + "; tick=" + ticker.read());
                        }
                    case "resize" ->
                        out.println(cache.resize(Integer.parseInt(single(data))));
                    case "policy" ->
                        {
                            policy = EvictionPolicy.valueOf(single(data));
                            out.println("Polityka kolejnego cache: " + policy);
                        }
                    case "wall" ->
                        {
                            clock.shift(Duration.ofSeconds(Long.parseLong(single(data))));
                            out.println("Data: " + clock.instant());
                        }
                    case "new" ->
                        {
                            var candidate = new InMemoryCache<String, String>(Integer.parseInt(single(data)), policy,
                                    ticker, clock);
                            cache = candidate;
                            out.println("Nowy cache");
                        }
                    default ->
                        {
                            if (!data.isEmpty())
                                throw new IllegalArgumentException("Komenda bez argumentów: " + name);
                            switch(name) {
                                case "help" ->
                                    out.println("put KEY VALUE; put-ttl KEY SECONDS VALUE; get KEY; peek KEY; remove KEY; show; size; clean; clear; time; advance SECONDS; wall SECONDS; policy LRU|LFU; resize CAPACITY; stats; diagnostics; reset-stats; new CAPACITY; q");
                                case "show" ->
                                    {
                                        var view = cache.snapshot();
                                        out.println("LRU -> MRU: " + view.leastToMostRecent().stream()
                                                .map(CacheItem::key).toList());
                                        view.leastToMostRecent().forEach(out::println);
                                    }
                                case "stats" ->
                                    out.println(cache.statistics());
                                case "diagnostics" ->
                                    out.println(cache.diagnostics());
                                case "reset-stats" ->
                                    {
                                        cache.resetStatistics();
                                        out.println("OK");
                                    }
                                case "size" ->
                                    out.println("Rozmiar: " + cache.size());
                                case "clean" ->
                                    out.println("Wygasłe usunięte: " + cache.cleanUp());
                                case "clear" ->
                                    {
                                        cache.clear();
                                        out.println("OK");
                                    }
                                case "time" ->
                                    out.println("Czas: " + clock.instant() + "; tick=" + ticker.read());
                                default ->
                                    throw new IllegalArgumentException("Nieznana komenda; help");
                            }
                        }
                }
            } catch (IllegalArgumentException | ArithmeticException | DateTimeException ex) {
                out.println("Odmowa: " + ex.getMessage());
            }
        }
        out.flush();
    }

    private static String single(String data) {
        if (data.isBlank() || data.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Wymagany jeden argument");
        }
        return data;
    }
}
