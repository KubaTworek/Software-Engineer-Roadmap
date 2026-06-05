package com.example.ecommerce.notification;

import com.example.ecommerce.order.CustomerOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendOrderConfirmation(CustomerOrder order) {
        log.info(
                "Mock email sent: order confirmation for orderNumber={}, userEmail={}, amount={} {}",
                order.getOrderNumber(),
                order.getUser().getEmail(),
                order.getTotalAmount(),
                order.getCurrency()
        );
    }
}
