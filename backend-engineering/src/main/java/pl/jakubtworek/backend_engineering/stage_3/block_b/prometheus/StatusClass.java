package pl.jakubtworek.backend_engineering.stage_3.block_b.prometheus;

/**
 * Represents low-cardinality HTTP status classes.
 *
 * Status class is safer than raw status code for external dependencies,
 * because it limits label cardinality while preserving operational meaning.
 */
public enum StatusClass {

    INFORMATIONAL_1XX("1xx"),
    SUCCESS_2XX("2xx"),
    REDIRECTION_3XX("3xx"),
    CLIENT_ERROR_4XX("4xx"),
    SERVER_ERROR_5XX("5xx"),
    UNKNOWN("unknown");

    private final String labelValue;

    StatusClass(String labelValue) {
        this.labelValue = labelValue;
    }

    public String labelValue() {
        return labelValue;
    }

    public static StatusClass fromStatusCode(int statusCode) {
        if (statusCode >= 100 && statusCode <= 199) {
            return INFORMATIONAL_1XX;
        }
        if (statusCode >= 200 && statusCode <= 299) {
            return SUCCESS_2XX;
        }
        if (statusCode >= 300 && statusCode <= 399) {
            return REDIRECTION_3XX;
        }
        if (statusCode >= 400 && statusCode <= 499) {
            return CLIENT_ERROR_4XX;
        }
        if (statusCode >= 500 && statusCode <= 599) {
            return SERVER_ERROR_5XX;
        }
        return UNKNOWN;
    }
}