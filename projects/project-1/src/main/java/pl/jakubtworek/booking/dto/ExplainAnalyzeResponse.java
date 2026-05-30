package pl.jakubtworek.booking.dto;

import java.util.List;

public record ExplainAnalyzeResponse(
        String queryName,
        List<String> plan
) {
}
