package com.example.notification.infrastructure.template;

import com.example.notification.application.Ports;
import com.example.notification.domain.NotificationTemplate;
import com.example.notification.domain.RenderedNotification;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SimpleTemplateRenderer implements Ports.TemplateRenderer {
    @Override
    public RenderedNotification render(NotificationTemplate template, Map<String, Object> payload) {
        return new RenderedNotification(renderText(template.getSubject(), payload), renderText(template.getBody(), payload));
    }

    private String renderText(String template, Map<String, Object> payload) {
        String rendered = template;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
                throw new IllegalArgumentException("Unsafe template variable name");
            }
            rendered = rendered.replace("{{" + entry.getKey() + "}}", escape(String.valueOf(entry.getValue())));
        }
        if (rendered.matches("(?s).*\\{\\{[^}]+}}.*")) {
            throw new IllegalArgumentException("Template contains unresolved variables");
        }
        return rendered;
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
