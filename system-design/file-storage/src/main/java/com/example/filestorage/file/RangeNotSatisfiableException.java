package com.example.filestorage.file;

public class RangeNotSatisfiableException extends RuntimeException {
    private final long resourceSize;
    public RangeNotSatisfiableException(long resourceSize, String message) {
        super(message);
        this.resourceSize = resourceSize;
    }
    public long getResourceSize() { return resourceSize; }
}
