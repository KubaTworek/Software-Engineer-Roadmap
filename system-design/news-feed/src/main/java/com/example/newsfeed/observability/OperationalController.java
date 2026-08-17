package com.example.newsfeed.observability;

import com.example.newsfeed.events.DeadLetterEventRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ops")
public class OperationalController {

    private final DeadLetterEventRepository deadLetterEventRepository;

    public OperationalController(DeadLetterEventRepository deadLetterEventRepository) {
        this.deadLetterEventRepository = deadLetterEventRepository;
    }

    @GetMapping("/dlq/count")
    public Map<String, Long> dlqCount() {
        return Map.of("deadLetterEvents", deadLetterEventRepository.count());
    }
}
