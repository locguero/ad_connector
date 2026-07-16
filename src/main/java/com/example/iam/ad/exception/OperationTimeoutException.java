package com.example.iam.ad.exception;

/** Connect or response timeout talking to a domain controller. */
public class OperationTimeoutException extends IamIntegrationException {

    public OperationTimeoutException(String message) {
        super(message);
    }

    public OperationTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
