package com.example.filestorage.sync;

import com.example.filestorage.config.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {
    private final ChangeLogService changeLogService;

    public SyncController(ChangeLogService changeLogService) {
        this.changeLogService = changeLogService;
    }

    @GetMapping("/changes")
    public SyncChangesResponse changes(CurrentUser currentUser,
                                       @RequestParam(defaultValue = "0") long cursor,
                                       @RequestParam(defaultValue = "100") int limit) {
        return changeLogService.changes(currentUser.id(), cursor, limit);
    }
}
