package com.example.notification.api;
import com.example.notification.api.dto.ApiDtos;
import com.example.notification.application.DigestService;
import com.example.notification.domain.DigestBuffer;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/digests")
public class DigestController {
    private final DigestService service; public DigestController(DigestService service){this.service=service;}
    @PostMapping("/buffer") public DigestBuffer buffer(@RequestHeader(value="X-Actor",required=false) String actor,@Valid @RequestBody ApiDtos.BufferDigestRequest r){return service.buffer(TenantContext.getTenantId(),actor,r.userId(),r.digestKey(),r.item());}
}
