package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller demonstrating custom HttpMessageConverter.
 */
@RestController
public class ReportController {

    /**
     * Produces CSV response.
     *
     * CsvMessageConverter will serialize CsvResponse
     * into text/csv response body.
     */
    @GetMapping(value = "/api/reports/users", produces = "text/csv")
    public ResponseEntity<CsvResponse> usersReport() {

        CsvResponse response = new CsvResponse(
                "id,username,email\n1,john,john@example.com"
        );

        return ResponseEntity.ok(response);
    }
}