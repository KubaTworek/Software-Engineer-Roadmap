package pl.jakubtworek.lld.workflow.domain;

/**
 * Niemutowalna treść rewizji: prosty tekst bez załączników. Usuwamy zewnętrzne
 * białe znaki, więc dodanie spacji na końcu nie udaje poprawy odrzuconej treści.
 */
public record DocumentContent(String title, String body) {

    public DocumentContent {
        if (title == null || body == null || title.isBlank() || body.isBlank())
            throw new IllegalArgumentException("Tytuł i treść są wymagane");
        title = title.strip();
        body = body.strip();
    }
}
