package com.example.notification;

import com.example.notification.domain.*;
import com.example.notification.infrastructure.template.SimpleTemplateRenderer;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class TemplateRendererTest {
    private final SimpleTemplateRenderer renderer = new SimpleTemplateRenderer();

    @Test void escapesUntrustedValues() {
        var template = new NotificationTemplate(UUID.randomUUID(), "tenant", "key", NotificationType.CAMPAIGN_MESSAGE,
                Channel.EMAIL, 1, "Hello {{name}}", "{{body}}", Set.of("name", "body"), true);
        var rendered = renderer.render(template, Map.of("name", "<script>", "body", "A&B"));
        assertThat(rendered.subject()).isEqualTo("Hello &lt;script&gt;");
        assertThat(rendered.body()).isEqualTo("A&amp;B");
    }

    @Test void rejectsUnresolvedVariables() {
        var template = new NotificationTemplate(UUID.randomUUID(), "tenant", "key", NotificationType.CAMPAIGN_MESSAGE,
                Channel.EMAIL, 1, "{{missing}}", "body", Set.of("missing"), true);
        assertThatThrownBy(() -> renderer.render(template, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
