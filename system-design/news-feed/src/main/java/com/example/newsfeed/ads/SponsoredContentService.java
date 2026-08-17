package com.example.newsfeed.ads;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SponsoredContentService {
    private final SponsoredCampaignRepository repository;
    private final boolean enabled;
    private final int maxItems;
    public SponsoredContentService(SponsoredCampaignRepository repository,
                                   @Value("${newsfeed.sponsored.enabled:true}") boolean enabled,
                                   @Value("${newsfeed.sponsored.max-items-per-feed:1}") int maxItems) {
        this.repository = repository; this.enabled = enabled; this.maxItems = maxItems;
    }
    public List<SponsoredCampaign> getCandidates() {
        if (!enabled) return List.of();
        return repository.findTop10ByStatusOrderByBidScoreDesc("active").stream().limit(maxItems).toList();
    }
}
