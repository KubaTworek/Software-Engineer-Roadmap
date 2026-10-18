package com.example.autocomplete.quality;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class RankingQualityEvaluatorTest {
    private final RankingQualityEvaluator evaluator = new RankingQualityEvaluator();

    @Test
    void rewardsPuttingHighlyRelevantSuggestionFirst() {
        Map<String, Integer> judgments = Map.of("iphone", 3, "case", 2, "charger", 1);

        double good = evaluator.ndcgAtK(List.of("iphone", "case", "charger"), judgments, 3);
        double regressed = evaluator.ndcgAtK(List.of("charger", "case", "iphone"), judgments, 3);

        assertThat(good).isEqualTo(1.0);
        assertThat(regressed).isLessThan(good);
        assertThat(evaluator.reciprocalRank(List.of("noise", "iphone"), Set.of("iphone"))).isEqualTo(.5);
    }
}
