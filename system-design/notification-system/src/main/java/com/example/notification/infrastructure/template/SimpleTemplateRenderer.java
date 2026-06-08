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
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }
}
