package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registers custom HttpMessageConverter.
 *
 * This extends Spring MVC message conversion pipeline.
 */
@Configuration
public class MessageConverterConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(
            List<HttpMessageConverter<?>> converters
    ) {
        converters.add(new CsvMessageConverter());
    }
}