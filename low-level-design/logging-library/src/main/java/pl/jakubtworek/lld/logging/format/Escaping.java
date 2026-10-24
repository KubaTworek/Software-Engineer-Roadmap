package pl.jakubtworek.lld.logging.format;

/**
 * Wspólne kodowanie znaków kontrolnych i znaków JSON. Rezultat nie zawiera
 * fizycznych CR/LF z komunikatu, więc dane nie udają dodatkowego rekordu.
 * Nie jest to anonimizacja: sekrety nadal pozostałyby sekretami w treści.
 */
final class Escaping {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private Escaping() {
    }

    static String escape(String value) {
        var out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch(c) {
                case '"' ->
                    out.append("\\\"");
                case '\\' ->
                    out.append("\\\\");
                case '\n' ->
                    out.append("\\n");
                case '\r' ->
                    out.append("\\r");
                case '\t' ->
                    out.append("\\t");
                default ->
                    {
                        if (c < 32 || c == 127)
                            out.append("\\u00").append(HEX[(c >>> 4) & 15]).append(HEX[c & 15]);
                        else
                            out.append(c);
                    }
            }
        }
        return out.toString();
    }
}
