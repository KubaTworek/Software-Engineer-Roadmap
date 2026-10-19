package com.example.notification.api;

import com.example.notification.api.dto.ApiDtos;
import com.example.notification.application.Ports;
import com.example.notification.domain.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Policy plane preferencji; worker sprawdza zgodę ponownie tuż przed efektem. */
@RestController @RequestMapping("/api/v1/users/{userId}/notification-preferences")
public class PreferenceController {
    private final Ports.PreferenceService preferences;
    public PreferenceController(Ports.PreferenceService preferences){this.preferences=preferences;}
    @GetMapping public Map<NotificationType,Map<Channel,Boolean>> get(@PathVariable String userId){return preferences.getPreferences(TenantContext.getTenantId(),userId);}
    @PutMapping public Map<NotificationType,Map<Channel,Boolean>> update(@PathVariable String userId,@Valid @RequestBody ApiDtos.UpdatePreferencesRequest r){return preferences.updatePreferences(TenantContext.getTenantId(),userId,r.preferences());}
}
