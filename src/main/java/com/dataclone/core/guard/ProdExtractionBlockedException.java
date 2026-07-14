package com.dataclone.core.guard;

/**
 * Exception thrown when extraction from a production database is attempted
 * before anonymization (T4) is implemented.
 */
public class ProdExtractionBlockedException extends RuntimeException {

    public ProdExtractionBlockedException(String message) {
        super(message);
    }
}
