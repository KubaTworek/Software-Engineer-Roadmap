package com.example.autocomplete.index;

import com.example.autocomplete.model.IndexedSuggestion;
import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.service.CanonicalKeyGenerator;
import com.example.autocomplete.service.TextNormalizer;

import java.util.*;

/**
 * Indeks autocomplete oparty o Trie.
 *
 * Trie pozwala szybko znaleźć sugestie po prefiksie.
 *
 * Przykład:
 * query = "iph"
 *
 * Zamiast skanować wszystkie sugestie, przechodzimy po znakach:
 * i -> p -> h
 *
 * i w węźle "iph" mamy już przygotowaną listę najlepszych kandydatów.
 *
 * Ta klasa odpowiada tylko za candidate generation.
 * Finalny ranking robi później SuggestionRanker.
 */
public class SuggestionTrieIndex implements AutocompleteIndex {

    /**
     * Wersja indeksu.
     *
     * Używana do:
     * - rollout/rollback,
     * - cache key,
     * - debugowania,
     * - nagłówków/metadanych odpowiedzi.
     */
    private final String version;

    /**
     * Korzeń drzewa Trie.
     *
     * Sam root nie reprezentuje żadnego znaku.
     * Od niego zaczynamy przechodzenie po znakach query.
     */
    private final TrieNode root = new TrieNode();

    /**
     * Wspólna normalizacja tekstu.
     *
     * Musi być taka sama jak w query processing,
     * bo indeksujemy i wyszukujemy po znormalizowanych wariantach.
     */
    private final TextNormalizer normalizer;

    /**
     * Generator kanonicznego klucza sugestii.
     *
     * Służy do deduplikacji.
     *
     * Przykład:
     * "iPhone 15 Pro"
     * "iphone-15-pro"
     * "IPHONE 15 PRO"
     *
     * powinny reprezentować tę samą sugestię logiczną.
     */
    private final CanonicalKeyGenerator keyGenerator;

    /**
     * Maksymalna liczba kandydatów przechowywana w jednym węźle Trie.
     *
     * To ważna optymalizacja pamięci.
     *
     * Nie chcemy, żeby każdy węzeł trzymał tysiące sugestii.
     * Trzymamy tylko top N według ORDER.
     */
    private final int maxCandidatesPerNode;

    /**
     * Liczba unikalnych sugestii po deduplikacji.
     *
     * To nie musi być równe liczbie wejściowych sugestii,
     * bo kilka sugestii może mieć ten sam canonicalKey.
     */
    private final int uniqueSuggestionCount;

    /**
     * Największa popularność w całym indeksie.
     *
     * Używana później przez ranker do normalizacji popularity score.
     */
    private final int maxPopularity;

    /**
     * Liczba wariantów tekstowych zaindeksowanych w Trie.
     *
     * Jedna sugestia może mieć wiele wariantów:
     * - displayText,
     * - aliasy.
     *
     * Przykład:
     * "Sony PlayStation 5"
     * aliasy:
     * - "ps5"
     * - "playstation"
     */
    private int indexedVariantsCount;

    /**
     * Liczba węzłów Trie.
     *
     * Startujemy od 1, bo root istnieje od początku.
     *
     * Przydatne do statystyk i oceny rozmiaru indeksu w pamięci.
     */
    private int trieNodeCount = 1;

    /**
     * Kolejność kandydatów przechowywanych w każdym węźle.
     *
     * Najpierw sortujemy po popularności malejąco,
     * potem po displayText dla stabilnego porządku.
     *
     * To jest ranking wstępny, nie finalny ranking użytkownika.
     */
    private static final Comparator<IndexedSuggestion> ORDER = Comparator
            .comparingInt((IndexedSuggestion i) -> i.suggestion().popularity()).reversed()
            .thenComparing(i -> i.suggestion().displayText());

    public SuggestionTrieIndex(
            String version,
            List<Suggestion> suggestions,
            TextNormalizer normalizer,
            CanonicalKeyGenerator keyGenerator,
            int maxCandidatesPerNode
    ) {
        this.version = version;
        this.normalizer = normalizer;
        this.keyGenerator = keyGenerator;
        this.maxCandidatesPerNode = maxCandidatesPerNode;

        /*
         * Najpierw deduplikujemy wejściowe sugestie.
         *
         * Nie chcemy mieć w indeksie kilku logicznie tych samych sugestii
         * różniących się tylko formatem zapisu.
         */
        List<Suggestion> deduped = deduplicate(suggestions);

        /*
         * Statystyki indeksu liczone po deduplikacji.
         */
        this.uniqueSuggestionCount = deduped.size();
        this.maxPopularity = deduped.stream()
                .mapToInt(Suggestion::popularity)
                .max()
                .orElse(0);

        /*
         * Budowa właściwego Trie.
         *
         * Po tym kroku indeks jest gotowy do obsługi candidates().
         */
        build(deduped);
    }

    /**
     * Zwraca kandydatów autocomplete dla raw query.
     *
     * Ta metoda jest wywoływana przez AutocompleteService.
     *
     * Nie robi:
     * - personalizacji,
     * - safety filtering,
     * - finalnego rankingu,
     * - cache.
     *
     * Robi tylko szybkie znalezienie kandydatów po prefiksie.
     */
    @Override
    public List<Suggestion> candidates(String rawQuery, int candidateLimit) {

        /*
         * Query normalizujemy tak samo, jak tekst użyty przy budowie indeksu.
         */
        String q = normalizer.normalize(rawQuery);

        /*
         * Minimalna długość query to 2 znaki.
         *
         * Dla jednego znaku liczba dopasowań może być ogromna,
         * a jakość sugestii zwykle niska.
         *
         * candidateLimit < 1 też nie ma sensu.
         */
        if (q.length() < 2 || candidateLimit < 1) {
            return List.of();
        }

        /*
         * Znajdujemy węzeł Trie odpowiadający całemu query.
         *
         * Jeśli query = "iph", szukamy węzła po ścieżce:
         * i -> p -> h.
         */
        TrieNode node = findNode(q);

        /*
         * Brak węzła oznacza brak kandydatów dla tego prefiksu.
         */
        if (node == null) {
            return List.of();
        }

        /*
         * Węzeł ma już gotową listę top kandydatów.
         *
         * Limitujemy wynik do candidateLimit i zdejmujemy wrapper IndexedSuggestion,
         * bo dalej pipeline pracuje na Suggestion.
         */
        return node.candidates()
                .stream()
                .limit(candidateLimit)
                .map(IndexedSuggestion::suggestion)
                .toList();
    }

    /**
     * Zwraca statystyki indeksu.
     *
     * Używane przez endpointy diagnostyczne/adminowe.
     */
    @Override
    public IndexStats stats() {
        return new IndexStats(
                version,
                uniqueSuggestionCount,
                indexedVariantsCount,
                trieNodeCount,
                maxCandidatesPerNode,
                maxPopularity
        );
    }

    /**
     * Zwraca wersję indeksu.
     *
     * Ta wartość trafia m.in. do cache key i response metadata.
     */
    @Override
    public String version() {
        return version;
    }

    /**
     * Usuwa duplikaty logicznych sugestii.
     *
     * Deduplikacja działa po canonicalKey.
     *
     * Jeśli kilka sugestii ma ten sam canonicalKey,
     * zostawiamy tę z największą popularnością.
     *
     * To zapobiega sytuacji, gdzie użytkownik widzi kilka prawie identycznych
     * wyników autocomplete.
     */
    private List<Suggestion> deduplicate(List<Suggestion> suggestions) {
        Map<String, Suggestion> best = new LinkedHashMap<>();

        for (Suggestion s : suggestions) {
            String key = keyGenerator.canonicalKey(s.displayText());
            Suggestion current = best.get(key);

            /*
             * Jeśli to pierwsza sugestia dla danego canonicalKey,
             * zapisujemy ją.
             *
             * Jeśli już istnieje, zostawiamy bardziej popularną.
             */
            if (current == null || s.popularity() > current.popularity()) {
                best.put(key, s);
            }
        }

        return new ArrayList<>(best.values());
    }

    /**
     * Buduje Trie ze zduplikowanej i oczyszczonej listy sugestii.
     *
     * Dla każdej sugestii indeksujemy:
     * - znormalizowany displayText,
     * - znormalizowane aliasy.
     *
     * Dzięki temu query może trafić zarówno po nazwie głównej,
     * jak i po alternatywnych zapisach.
     */
    private void build(List<Suggestion> suggestions) {
        for (Suggestion s : suggestions) {

            /*
             * IndexedSuggestion zawiera sugestię oraz jej canonicalKey.
             *
             * canonicalKey jest potrzebny, żeby nie dodawać tej samej sugestii
             * kilka razy do listy kandydatów jednego węzła.
             */
            IndexedSuggestion indexed = new IndexedSuggestion(
                    s,
                    keyGenerator.canonicalKey(s.displayText())
            );

            /*
             * Set usuwa duplikaty wariantów.
             *
             * Przykład:
             * displayText i alias po normalizacji mogą dać ten sam tekst.
             */
            Set<String> variants = new HashSet<>();

            /*
             * Główny wariant tekstowy sugestii.
             */
            variants.add(normalizer.normalize(s.displayText()));

            /*
             * Aliasowe warianty tekstowe sugestii.
             *
             * Każdy alias może umożliwić inne dopasowanie.
             */
            for (String alias : s.aliases()) {
                String a = normalizer.normalize(alias);

                /*
                 * Nie indeksujemy pustych aliasów.
                 */
                if (!a.isBlank()) {
                    variants.add(a);
                }
            }

            /*
             * Każdy wariant trafia osobno do Trie.
             *
             * Jedna sugestia może więc być odnaleziona wieloma ścieżkami.
             */
            for (String variant : variants) {
                insert(variant, indexed);
                indexedVariantsCount++;
            }
        }
    }

    /**
     * Wstawia jeden wariant tekstowy do Trie.
     *
     * Dla każdego prefiksu wariantu aktualizujemy listę najlepszych kandydatów.
     *
     * Przykład:
     * variant = "iphone"
     *
     * aktualizowane węzły:
     * i
     * ip
     * iph
     * ipho
     * iphon
     * iphone
     *
     * Dzięki temu query "iph" może od razu znaleźć kandydatów
     * bez skanowania całego indeksu.
     */
    private void insert(String variant, IndexedSuggestion suggestion) {
        TrieNode current = root;

        for (char c : variant.toCharArray()) {

            /*
             * Przechodzimy do dziecka dla danego znaku.
             *
             * Jeśli węzeł nie istnieje, tworzymy go i zwiększamy licznik.
             */
            current = current.children().computeIfAbsent(c, ignored -> {
                trieNodeCount++;
                return new TrieNode();
            });

            /*
             * Nie dodajemy tej samej logicznej sugestii dwa razy
             * do tego samego węzła.
             *
             * Może się to zdarzyć, gdy displayText i alias prowadzą
             * do tego samego prefiksu.
             */
            boolean exists = current.candidates()
                    .stream()
                    .anyMatch(e -> e.canonicalKey().equals(suggestion.canonicalKey()));

            if (!exists) {
                current.candidates().add(suggestion);
            }

            /*
             * Po dodaniu kandydata utrzymujemy listę węzła w dobrej kolejności.
             *
             * To jest pre-ranking po popularności.
             */
            current.candidates().sort(ORDER);

            /*
             * Ograniczamy liczbę kandydatów w węźle.
             *
             * Dzięki temu indeks ma kontrolowany rozmiar w pamięci.
             * Usuwamy ostatni element, bo po sortowaniu jest najsłabszy.
             */
            if (current.candidates().size() > maxCandidatesPerNode) {
                current.candidates().remove(current.candidates().size() - 1);
            }
        }
    }

    /**
     * Znajduje węzeł Trie odpowiadający całemu query.
     *
     * Jeśli którykolwiek znak po drodze nie istnieje,
     * oznacza to brak kandydatów dla danego prefiksu.
     */
    private TrieNode findNode(String query) {
        TrieNode current = root;

        for (char c : query.toCharArray()) {
            current = current.children().get(c);

            if (current == null) {
                return null;
            }
        }

        return current;
    }
}