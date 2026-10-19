package com.example.notification.api;
import com.example.notification.application.PersonalDataLifecycleService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/users/{userId}/personal-data")
public class PersonalDataController {
    private final PersonalDataLifecycleService lifecycle;
    public PersonalDataController(PersonalDataLifecycleService lifecycle){this.lifecycle=lifecycle;}
    @DeleteMapping public PersonalDataLifecycleService.ErasureResult erase(@PathVariable String userId){return lifecycle.erase(TenantContext.getTenantId(),userId);}
}
