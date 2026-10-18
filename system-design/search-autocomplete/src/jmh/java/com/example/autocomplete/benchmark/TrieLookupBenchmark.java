package com.example.autocomplete.benchmark;

import com.example.autocomplete.index.SuggestionTrieIndex;
import com.example.autocomplete.model.Suggestion;
import com.example.autocomplete.model.SuggestionMetrics;
import com.example.autocomplete.service.CanonicalKeyGenerator;
import com.example.autocomplete.service.TextNormalizer;
import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 1, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 1, time = 100, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class TrieLookupBenchmark {
    private SuggestionTrieIndex index;

    @Setup
    public void setup() {
        var normalizer = new TextNormalizer();
        List<Suggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            suggestions.add(new Suggestion("id-" + i, "product " + i, "query",
                    new SuggestionMetrics(10_000 - i, .2, .1, .3, .9),
                    List.of(), Set.of(), Set.of(), Set.of(), false));
        }
        index = new SuggestionTrieIndex("benchmark", suggestions, normalizer,
                new CanonicalKeyGenerator(normalizer), 200);
    }

    @Benchmark
    public List<Suggestion> hotPrefixLookup() {
        return index.candidates("product 99", 100);
    }
}
