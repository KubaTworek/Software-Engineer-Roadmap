package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers custom HttpMessageConverter.
 *
 * This extends Spring MVC message conversion pipeline.
 */
@Configuration
public class MessageConverterConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.addCustomConverter(new CsvMessageConverter());
    }
}
