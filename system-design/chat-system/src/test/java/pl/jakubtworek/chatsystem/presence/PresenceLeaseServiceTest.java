package pl.jakubtworek.chatsystem.presence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.user.AppUser;
import pl.jakubtworek.chatsystem.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PresenceLeaseServiceTest {
    @Autowired PresenceLeaseService leases;
    @Autowired PresenceService presence;
    @Autowired UserRepository users;

    @Test
    void userGoesOfflineOnlyAfterLastDeviceDisconnects() {
        AppUser user = TestUsers.create(users, "presence_multi_device");

        assertThat(leases.connected("phone", user.getId())).isPresent();
        assertThat(leases.connected("laptop", user.getId())).isEmpty();
        assertThat(presence.isOnline(user.getId())).isTrue();

        assertThat(leases.disconnected("phone", user.getId())).isEmpty();
        assertThat(presence.isOnline(user.getId())).isTrue();

        assertThat(leases.disconnected("laptop", user.getId())).isPresent();
        assertThat(presence.isOnline(user.getId())).isFalse();
    }
}
