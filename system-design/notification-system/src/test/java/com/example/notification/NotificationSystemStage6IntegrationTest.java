package com.example.notification;

import com.example.notification.api.dto.ApiDtos;
import com.example.notification.domain.Channel;
import com.example.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationSystemStage6IntegrationTest {
    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldCreateNotificationForTenant() {
        ApiDtos.CreateNotificationRequest request = new ApiDtos.CreateNotificationRequest(
                "user-1",
                NotificationType.PAYMENT_FAILED,
                List.of(Channel.EMAIL),
                new ApiDtos.ContactPointRequest("user@example.com", "+48123123123", "push-token"),
                Map.of("firstName", "Jakub", "invoiceId", "INV-1", "amount", "99 PLN"),
                "payment:user-1:INV-1",
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", "tenant-a");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/notifications", new HttpEntity<>(request, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Map notification = (Map) response.getBody().get("notification");
        assertThat(notification.get("tenantId")).isEqualTo("tenant-a");
    }

    @Test
    void shouldReturnDuplicateForSameIdempotencyKey() {
        ApiDtos.CreateNotificationRequest request = new ApiDtos.CreateNotificationRequest(
                "user-2",
                NotificationType.PAYMENT_FAILED,
                List.of(Channel.EMAIL),
                new ApiDtos.ContactPointRequest("user2@example.com", "+48123123123", "push-token"),
                Map.of("firstName", "Anna", "invoiceId", "INV-2", "amount", "149 PLN"),
                "payment:user-2:INV-2",
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Tenant-Id", "default");

        restTemplate.postForEntity("/api/v1/notifications", new HttpEntity<>(request, headers), Map.class);
        ResponseEntity<Map> second = restTemplate.postForEntity("/api/v1/notifications", new HttpEntity<>(request, headers), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(second.getBody().get("duplicate")).isEqualTo(true);
    }

    @Test
    void shouldExposeAdminDashboard() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-Id", "default");

        ResponseEntity<Map> response = restTemplate.exchange("/api/v1/admin/ops/dashboard", HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKeys("queueSize", "jobs", "dlq", "outbox", "auditEvents");
    }
}
