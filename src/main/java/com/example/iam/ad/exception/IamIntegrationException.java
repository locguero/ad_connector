package com.example.iam.ad.exception;

/**
 * Base runtime exception for all AD connector failures. The upstream
 * framework branches on this hierarchy and never sees raw UnboundID
 * exceptions.
 */
public class IamIntegrationException extends RuntimeException {

    public IamIntegrationException(String message) {
        super(message);
    }

    public IamIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
