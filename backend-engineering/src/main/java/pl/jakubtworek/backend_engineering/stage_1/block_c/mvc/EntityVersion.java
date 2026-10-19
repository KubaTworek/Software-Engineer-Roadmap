package pl.jakubtworek.backend_engineering.stage_1.block_c.mvc;

/** Strong HTTP entity-tag backed by the resource version. */
public record EntityVersion(long value) {

    public EntityVersion {
        if (value < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
    }

    public String toEntityTag() {
        return "\"" + value + "\"";
    }

    public static EntityVersion parseStrongEntityTag(String header) {
        if (header == null) {
            throw new PreconditionRequiredException("If-Match header is required");
        }
        if (header.length() < 3 || header.charAt(0) != '"'
                || header.charAt(header.length() - 1) != '"') {
            throw new IllegalArgumentException("If-Match must contain one strong numeric ETag");
        }
        String rawVersion = header.substring(1, header.length() - 1);
        try {
            return new EntityVersion(Long.parseLong(rawVersion));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match must contain one strong numeric ETag");
        }
    }
}
