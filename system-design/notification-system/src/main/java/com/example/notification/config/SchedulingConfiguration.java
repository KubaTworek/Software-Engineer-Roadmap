package com.example.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling is enabled in normal runtime. Tests driving the pipeline manually
 * disable registration of all scheduled callbacks, not the services themselves.
 * A large fixedDelay is not a substitute: the first callback runs immediately.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "notification.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {}
