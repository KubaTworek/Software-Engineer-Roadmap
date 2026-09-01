package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SafeOutboundRequestPolicyTest {

    private final SafeOutboundRequestPolicy policy =
            new SafeOutboundRequestPolicy(Set.of("images.example.com"));

    @Test
    void exactHttpsHostIsAllowed() {
        assertThat(policy.validate(URI.create("https://images.example.com/a/../avatar.png")))
                .isEqualTo(URI.create("https://images.example.com/avatar.png"));
    }

    @Test
    void metadataIpUserInfoSuffixAndRedirectEscapeAreRejected() {
        assertRejected("http://images.example.com/avatar.png");
        assertRejected("https://169.254.169.254/latest/meta-data");
        assertRejected("https://images.example.com@169.254.169.254/latest");
        assertRejected("https://images.example.com.attacker.test/avatar.png");

        assertThatThrownBy(() -> policy.validateRedirect(URI.create("https://localhost/admin")))
                .isInstanceOf(SafeOutboundRequestPolicy.OutboundRequestRejectedException.class);
    }

    private void assertRejected(String uri) {
        assertThatThrownBy(() -> policy.validate(URI.create(uri)))
                .isInstanceOf(SafeOutboundRequestPolicy.OutboundRequestRejectedException.class);
    }
}
