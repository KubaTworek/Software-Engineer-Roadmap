package com.example.notification.api;

import com.example.notification.api.dto.ApiDtos;
import com.example.notification.application.CampaignService;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Control plane kampanii; start uruchamia jeden claimowany batch, nie całą listę bez limitu. */
@RestController @RequestMapping("/api/v1/admin/campaigns")
public class AdminCampaignController {
    private final Ports.CampaignRepository repository; private final CampaignService service;
    public AdminCampaignController(Ports.CampaignRepository repository,CampaignService service){this.repository=repository;this.service=service;}
    @PostMapping public Campaign create(@RequestHeader(value="X-Actor",required=false) String actor,@Valid @RequestBody ApiDtos.CreateCampaignRequest r){return service.create(new Campaign(UUID.randomUUID(),TenantContext.getTenantId(),r.name(),r.notificationType(),r.userIds(),r.channels(),r.payload()),actor);}
    /**
     * Endpoint wykonuje jeden ograniczony batch. Resolver jest symulacją Profile Service;
     * claim i checkpoint znajdują się w CampaignService/repository, nie w warstwie HTTP.
     */
    @PostMapping("/{id}/start") public Campaign start(
            @RequestHeader(value="X-Actor",required=false) String actor, @PathVariable UUID id) {
        return service.start(TenantContext.getTenantId(), actor, id,
                user -> new ContactPoint(user + "@example.com", "+48123123123", "push-" + user));
    }
    @GetMapping public List<Campaign> all(){return repository.findAll(TenantContext.getTenantId());}
}
