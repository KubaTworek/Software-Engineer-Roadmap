package pl.jakubtworek.backend.common.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@org.springframework.context.annotation.Import(pl.jakubtworek.backend.common.security.TicketingSecurityConfiguration.class)
public class CommonWebConfig {
    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorrelationIdFilter());
        registration.setOrder(1);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestLoggingFilter());
        registration.setOrder(2);
        return registration;
    }

    @Bean
    FilterRegistrationBean<DeadlineFilter> deadlineFilter() {
        FilterRegistrationBean<DeadlineFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new DeadlineFilter());
        registration.setOrder(0);
        return registration;
    }
}
