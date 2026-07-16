package com.example.iam.ad.exception;

/**
 * The server refused to return the full result set. Searches issued through
 * this connector use the Simple Paged Results control, so seeing this
 * usually means a caller bypassed pagination or hit an administrative limit.
 */
public class SizeLimitExceededException extends IamIntegrationException {

    public SizeLimitExceededException(String message) {
        super(message);
    }

    public SizeLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
