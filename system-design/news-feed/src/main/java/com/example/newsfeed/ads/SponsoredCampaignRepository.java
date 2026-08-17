package com.example.newsfeed.ads;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; import java.util.UUID;
public interface SponsoredCampaignRepository extends JpaRepository<SponsoredCampaign, UUID> {
    List<SponsoredCampaign> findTop10ByStatusOrderByBidScoreDesc(String status);
}
