package com.example.newsfeed.reconciliation;

import com.example.newsfeed.auth.CurrentUser;
import com.example.newsfeed.common.UnauthorizedException;
import com.example.newsfeed.user.User;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ops/reconciliation")
public class ReconciliationController {
    private final ReconciliationService service;
    public ReconciliationController(ReconciliationService service) { this.service = service; }

    @PostMapping("/search")
    public ReconciliationReport search(@CurrentUser User operator,
                                       @RequestParam(defaultValue = "500") int limit) {
        requireAdmin(operator); return service.rebuildSearch(limit);
    }

    @PostMapping("/feeds/{userId}")
    public ReconciliationReport feed(@CurrentUser User operator, @PathVariable UUID userId,
                                     @RequestParam(defaultValue = "50") int perAuthor) {
        requireAdmin(operator); return service.rebuildUserFeed(userId, perAuthor);
    }

    private void requireAdmin(User user) {
        if (user.getRole() != com.example.newsfeed.user.UserRole.ADMIN)
            throw new UnauthorizedException("Administrator role is required.");
    }
}
