package com.example.newsfeed.feed;

import com.example.newsfeed.ranking.RankedFeedCandidate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FeedMixer {

    private static final int MAX_CONSECUTIVE_SAME_AUTHOR = 2;
    private static final int MAX_RECOMMENDED_PER_10_ITEMS = 3;
    private static final int MAX_TRENDING_PER_10_ITEMS = 2;

    public List<RankedFeedCandidate> mix(List<RankedFeedCandidate> rankedCandidates, int maxItems) {
        List<RankedFeedCandidate> output = new ArrayList<>();
        Set<UUID> seenPostIds = new HashSet<>();
        Map<FeedSource, Integer> sourceCounters = new EnumMap<>(FeedSource.class);

        UUID lastAuthorId = null;
        int sameAuthorStreak = 0;

        for (RankedFeedCandidate ranked : rankedCandidates) {
            UUID postId = ranked.candidate().post().getId();
            UUID authorId = ranked.candidate().post().getAuthor().getId();
            FeedSource source = ranked.candidate().source();

            if (seenPostIds.contains(postId)) {
                continue;
            }

            if (lastAuthorId != null && lastAuthorId.equals(authorId) && sameAuthorStreak >= MAX_CONSECUTIVE_SAME_AUTHOR) {
                continue;
            }

            if (!sourceAllowed(source, output.size(), sourceCounters)) {
                continue;
            }

            output.add(ranked);
            seenPostIds.add(postId);
            sourceCounters.merge(source, 1, Integer::sum);

            if (lastAuthorId != null && lastAuthorId.equals(authorId)) {
                sameAuthorStreak++;
            } else {
                lastAuthorId = authorId;
                sameAuthorStreak = 1;
            }

            if (output.size() >= maxItems) {
                break;
            }
        }

        return output;
    }

    private boolean sourceAllowed(FeedSource source, int currentSize, Map<FeedSource, Integer> sourceCounters) {
        int bucketSize = Math.max(1, currentSize / 10 + 1);
        int recommendedLimit = bucketSize * MAX_RECOMMENDED_PER_10_ITEMS;
        int trendingLimit = bucketSize * MAX_TRENDING_PER_10_ITEMS;

        if (source == FeedSource.RECOMMENDED) {
            return sourceCounters.getOrDefault(source, 0) < recommendedLimit;
        }
        if (source == FeedSource.TRENDING) {
            return sourceCounters.getOrDefault(source, 0) < trendingLimit;
        }
        return true;
    }
}
