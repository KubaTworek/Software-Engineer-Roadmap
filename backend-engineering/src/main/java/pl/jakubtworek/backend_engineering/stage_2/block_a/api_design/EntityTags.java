package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

final class EntityTags {

    private EntityTags() {
    }

    static String fromVersion(long version) {
        return "\"v" + version + "\"";
    }

    static void requireCurrent(String ifMatch, long currentVersion) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiFailure.preconditionRequired();
        }

        String current = fromVersion(currentVersion);
        if (!current.equals(ifMatch.trim())) {
            throw ApiFailure.preconditionFailed(current, ifMatch);
        }
    }
}
