package com.example.iam.ad.exception;

/** The service account lacks the AD permissions required for the operation. */
public class InsufficientAccessException extends IamIntegrationException {

    public InsufficientAccessException(String message) {
        super(message);
    }

    public InsufficientAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
