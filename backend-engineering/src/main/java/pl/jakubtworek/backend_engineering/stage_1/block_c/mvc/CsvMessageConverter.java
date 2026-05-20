package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.AbstractHttpMessageConverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom HttpMessageConverter example.
 *
 * Message converters are responsible for converting:
 * - HTTP request body into Java object,
 * - Java object into HTTP response body.
 *
 * JSON is usually handled by MappingJackson2HttpMessageConverter.
 */
public class CsvMessageConverter
        extends AbstractHttpMessageConverter<CsvResponse> {

    public CsvMessageConverter() {
        super(CsvResponse.TEXT_CSV);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return CsvResponse.class.equals(clazz);
    }

    /**
     * Reads request body.
     *
     * Not implemented here because this converter is response-only.
     */
    @Override
    protected CsvResponse readInternal(
            Class<? extends CsvResponse> clazz,
            HttpInputMessage inputMessage
    ) {
        throw new UnsupportedOperationException("CSV input is not supported");
    }

    /**
     * Writes Java object as HTTP response body.
     */
    @Override
    protected void writeInternal(
            CsvResponse csvResponse,
            HttpOutputMessage outputMessage
    ) throws IOException {

        outputMessage.getBody()
                .write(csvResponse.content().getBytes(StandardCharsets.UTF_8));
    }
}