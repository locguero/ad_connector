package com.example.iam.ad.exception;

/** An add/modify collided with an existing entry or attribute value. */
public class ObjectAlreadyExistsException extends IamIntegrationException {

    public ObjectAlreadyExistsException(String message) {
        super(message);
    }

    public ObjectAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
