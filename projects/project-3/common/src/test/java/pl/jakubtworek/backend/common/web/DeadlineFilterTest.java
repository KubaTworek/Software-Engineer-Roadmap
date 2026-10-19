package pl.jakubtworek.backend.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class DeadlineFilterTest {
    @Test
    void expiredUpstreamDeadlineFailsBeforeBusinessWorkStarts() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(DeadlineContext.HEADER, "1");
        var response = new MockHttpServletResponse();
        new DeadlineFilter().doFilter(request, response, (req, res) -> {
            throw new AssertionError("chain must not run");
        });
        assertThat(response.getStatus()).isEqualTo(504);
    }
}
