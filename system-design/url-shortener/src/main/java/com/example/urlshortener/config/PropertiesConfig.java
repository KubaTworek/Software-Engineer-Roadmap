package com.example.urlshortener.config;

import com.example.urlshortener.abuse.AbuseDetectionProperties;
import com.example.urlshortener.admin.AdminProperties;
import com.example.urlshortener.analytics.AnalyticsProperties;
import com.example.urlshortener.queue.QueueProperties;
import com.example.urlshortener.region.RegionProperties;
import com.example.urlshortener.edge.EdgeProperties;
import com.example.urlshortener.enterprise.EnterpriseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
    RateLimitProperties.class,
    AnalyticsProperties.class,
    AdminProperties.class,
    QueueProperties.class,
    AbuseDetectionProperties.class,
    ReadReplicaProperties.class,
    RegionProperties.class,
    EdgeProperties.class,
    EnterpriseProperties.class
})
public class PropertiesConfig {
}
