package com.example.autocomplete.quality;

import org.springframework.stereotype.Component;

import java.util.*;

/** Offline relevance metrics; nie są metrykami runtime ani substytutem A/B testu. */
@Component
public class RankingQualityEvaluator {
    public double reciprocalRank(List<String> rankedIds, Set<String> relevantIds) {
        for (int index = 0; index < rankedIds.size(); index++) {
            if (relevantIds.contains(rankedIds.get(index))) return 1.0 / (index + 1);
        }
        return 0.0;
    }

    public double ndcgAtK(List<String> rankedIds, Map<String, Integer> relevance, int k) {
        double actual = dcg(rankedIds, relevance, k);
        List<String> ideal = relevance.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).toList();
        double best = dcg(ideal, relevance, k);
        return best == 0 ? 0 : actual / best;
    }

    private double dcg(List<String> ids, Map<String, Integer> relevance, int k) {
        double result = 0;
        for (int index = 0; index < Math.min(k, ids.size()); index++) {
            int grade = relevance.getOrDefault(ids.get(index), 0);
            result += (Math.pow(2, grade) - 1) / (Math.log(index + 2) / Math.log(2));
        }
        return result;
    }
}
