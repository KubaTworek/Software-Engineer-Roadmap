package pl.jakubtworek.chatsystem.conversation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.blocking.BlockingService;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConversationServiceTest {
    @Autowired ConversationService conversationService;
    @Autowired BlockingService blockingService;
    @Autowired UserRepository userRepository;

    @Test
    void createDirectConversationIsIdempotent() {
        AppUser alice = TestUsers.create(userRepository, "alice_direct");
        AppUser bob = TestUsers.create(userRepository, "bob_direct");

        ConversationResponse first = conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(bob.getId()));
        ConversationResponse second = conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(bob.getId()));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.type()).isEqualTo(ConversationType.DIRECT);
        assertThat(first.members()).hasSize(2);
    }

    @Test
    void createDirectConversationRejectsSelfAndBlockedUsers() {
        AppUser alice = TestUsers.create(userRepository, "alice_direct_blocked");
        AppUser bob = TestUsers.create(userRepository, "bob_direct_blocked");

        assertThatThrownBy(() -> conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(alice.getId())))
                .isInstanceOf(BadRequestException.class);

        blockingService.block(bob.getId(), alice.getId());
        assertThatThrownBy(() -> conversationService.createDirectConversation(alice.getId(), new CreateDirectConversationRequest(bob.getId())))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void groupOwnerCanAddMemberAndPromoteRole() {
        AppUser owner = TestUsers.create(userRepository, "owner_group");
        AppUser member = TestUsers.create(userRepository, "member_group");
        AppUser later = TestUsers.create(userRepository, "later_group");

        ConversationResponse group = conversationService.createGroupConversation(
                owner.getId(),
                new CreateGroupConversationRequest("Team", Set.of(member.getId()))
        );

        ConversationResponse afterAdd = conversationService.addMember(
                owner.getId(),
                group.id(),
                new AddGroupMemberRequest(later.getId(), ConversationRole.MEMBER)
        );
        assertThat(afterAdd.members()).hasSize(3);

        ConversationResponse afterPromote = conversationService.updateMemberRole(
                owner.getId(),
                group.id(),
                later.getId(),
                new UpdateMemberRoleRequest(ConversationRole.ADMIN)
        );
        assertThat(afterPromote.members()).anyMatch(m -> m.userId().equals(later.getId()) && m.role() == ConversationRole.ADMIN);
    }
}
