package pl.jakubtworek.backend_systems_lab_stage_1.block_c.mvc;

import org.springframework.http.MediaType;

/**
 * Example custom response type.
 *
 * It could be serialized by a custom HttpMessageConverter.
 */
public record CsvResponse(
        String content
) {
    public static final MediaType TEXT_CSV =
            MediaType.parseMediaType("text/csv");
}