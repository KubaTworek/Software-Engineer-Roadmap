package com.example.notification.api;

import com.example.notification.api.dto.ApiDtos;
import com.example.notification.application.ProviderWebhookService;
import com.example.notification.application.WebhookSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Evidence plane: podpis jest sprawdzany po raw body przed deserializacją. */
@RestController @RequestMapping("/api/v1/provider-webhooks")
public class ProviderWebhookController {
    private final ProviderWebhookService webhooks; private final WebhookSignatureVerifier signatures; private final ObjectMapper mapper;
    public ProviderWebhookController(ProviderWebhookService webhooks,WebhookSignatureVerifier signatures,ObjectMapper mapper){this.webhooks=webhooks;this.signatures=signatures;this.mapper=mapper;}
    @PostMapping public Map<String,Object> handle(@RequestHeader(value="X-Webhook-Id",required=false) String eventId,
            @RequestHeader("X-Webhook-Timestamp") long timestamp,@RequestHeader("X-Webhook-Signature") String signature,
            @RequestBody String rawBody) throws Exception {
        signatures.verify(timestamp,signature,rawBody);
        ApiDtos.ProviderWebhookRequest r=mapper.readValue(rawBody,ApiDtos.ProviderWebhookRequest.class);
        boolean processed=webhooks.accept(eventId==null||eventId.isBlank()?timestamp+":"+signature:eventId,r.providerMessageId(),r.status(),r.reason());
        return Map.of("accepted",true,"processed",processed);
    }
}
