package com.example.notification.api;

import com.example.notification.api.dto.ApiDtos;
import com.example.notification.application.Ports;
import com.example.notification.application.TemplateManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Control plane wersjonowanych template'ów; stare wersje pozostają potrzebne jobom już zakolejkowanym. */
@RestController @RequestMapping("/api/v1/admin/templates")
public class AdminTemplateController {
    private final Ports.TemplateRepository repository; private final TemplateManagementService service;
    public AdminTemplateController(Ports.TemplateRepository repository,TemplateManagementService service){this.repository=repository;this.service=service;}
    @GetMapping public List<ApiDtos.TemplateResponse> all(){return repository.findAll(TenantContext.getTenantId()).stream().map(ApiDtos.TemplateResponse::from).toList();}
    @PostMapping public ApiDtos.TemplateResponse create(@RequestHeader(value="X-Actor",required=false) String actor,@Valid @RequestBody ApiDtos.TemplateRequest r){return ApiDtos.TemplateResponse.from(service.create(TenantContext.getTenantId(),actor,r.templateKey(),r.notificationType(),r.channel(),r.subject(),r.body(),r.requiredVariables()==null?Set.of():r.requiredVariables()));}
    @PutMapping("/{id}") public ApiDtos.TemplateResponse update(@RequestHeader(value="X-Actor",required=false) String actor,@PathVariable UUID id,@Valid @RequestBody ApiDtos.TemplateRequest r){return ApiDtos.TemplateResponse.from(service.update(TenantContext.getTenantId(),actor,id,r.subject(),r.body(),r.requiredVariables()==null?Set.of():r.requiredVariables(),true));}
    @DeleteMapping("/{id}") public void delete(@RequestHeader(value="X-Actor",required=false) String actor,@PathVariable UUID id){service.delete(TenantContext.getTenantId(),actor,id);}
}
