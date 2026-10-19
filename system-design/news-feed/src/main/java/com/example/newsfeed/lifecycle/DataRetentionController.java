package com.example.newsfeed.lifecycle;

import com.example.newsfeed.auth.CurrentUser;
import com.example.newsfeed.common.UnauthorizedException;
import com.example.newsfeed.user.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ops/retention")
public class DataRetentionController {
    private final DataRetentionService service;
    public DataRetentionController(DataRetentionService service) { this.service = service; }

    @PostMapping("/run")
    public RetentionReport run(@CurrentUser User operator) {
        if (operator.getRole() != UserRole.ADMIN) throw new UnauthorizedException("Administrator role is required.");
        return service.runOnce();
    }
}
