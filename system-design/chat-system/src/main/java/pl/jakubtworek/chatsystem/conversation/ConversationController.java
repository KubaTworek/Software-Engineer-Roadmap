package pl.jakubtworek.chatsystem.conversation;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/direct")
    public ConversationResponse createDirectConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateDirectConversationRequest request
    ) {
        return conversationService.createDirectConversation(principal.id(), request);
    }

    @GetMapping
    public List<ConversationResponse> myConversations(@AuthenticationPrincipal UserPrincipal principal) {
        return conversationService.getMyConversations(principal.id());
    }

    @GetMapping("/{conversationId}")
    public ConversationResponse getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID conversationId
    ) {
        return conversationService.getConversation(principal.id(), conversationId);
    }
}
