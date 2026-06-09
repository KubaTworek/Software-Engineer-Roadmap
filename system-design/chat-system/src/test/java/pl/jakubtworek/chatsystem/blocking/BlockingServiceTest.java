package pl.jakubtworek.chatsystem.blocking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.common.BadRequestException;
import pl.jakubtworek.chatsystem.common.ForbiddenException;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BlockingServiceTest {
    @Autowired BlockingService blockingService;
    @Autowired UserRepository userRepository;

    @Test
    void blockIsIdempotentAndPreventsContactEitherWay() {
        AppUser alice = TestUsers.create(userRepository, "alice_block");
        AppUser bob = TestUsers.create(userRepository, "bob_block");

        BlockedUserResponse first = blockingService.block(alice.getId(), bob.getId());
        BlockedUserResponse second = blockingService.block(alice.getId(), bob.getId());

        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(blockingService.getMyBlockedUsers(alice.getId())).hasSize(1);
        assertThatThrownBy(() -> blockingService.ensureNotBlockedEitherWay(alice.getId(), bob.getId()))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> blockingService.ensureNotBlockedEitherWay(bob.getId(), alice.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cannotBlockYourself() {
        AppUser alice = TestUsers.create(userRepository, "alice_self_block");

        assertThatThrownBy(() -> blockingService.block(alice.getId(), alice.getId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void unblockIsIdempotent() {
        AppUser alice = TestUsers.create(userRepository, "alice_unblock");
        AppUser bob = TestUsers.create(userRepository, "bob_unblock");
        blockingService.block(alice.getId(), bob.getId());

        blockingService.unblock(alice.getId(), bob.getId());
        blockingService.unblock(alice.getId(), bob.getId());

        assertThat(blockingService.getMyBlockedUsers(alice.getId())).isEmpty();
    }
}
