package com.example.iam.ad.exception;

/**
 * The referenced directory object (user, group, OU) does not exist — or does
 * not exist <em>yet</em> on the DC that answered, which is why the
 * replication-lag retry wrapper treats this exception as retryable.
 */
public class ObjectNotFoundException extends IamIntegrationException {

    public ObjectNotFoundException(String message) {
        super(message);
    }

    public ObjectNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
