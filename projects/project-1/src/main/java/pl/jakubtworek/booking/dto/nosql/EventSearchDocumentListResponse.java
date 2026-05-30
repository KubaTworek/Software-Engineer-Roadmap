package pl.jakubtworek.booking.dto.nosql;

import java.util.List;

public record EventSearchDocumentListResponse(
        int size,
        List<EventSearchDocumentResponse> items
) {
}
