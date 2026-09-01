package pl.jakubtworek.cloudarchitecture.service;

/** Domain exception used when a requested resource does not exist. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
