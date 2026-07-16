package com.example.iam.ad.exception;

import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;

/**
 * Single choke point that translates UnboundID {@link LDAPException}s into
 * the connector's typed exception hierarchy so LDAP SDK types never leak
 * upstream.
 */
public final class LdapExceptionMapper {

    private LdapExceptionMapper() {
    }

    public static IamIntegrationException map(String operation, LDAPException e) {
        ResultCode rc = e.getResultCode();
        String detail = e.getDiagnosticMessage() != null ? e.getDiagnosticMessage() : e.getMessage();
        String message = "%s failed [%s]: %s".formatted(operation, rc, detail);

        if (rc == ResultCode.NO_SUCH_OBJECT) {
            return new ObjectNotFoundException(message, e);
        }
        if (rc == ResultCode.ENTRY_ALREADY_EXISTS || rc == ResultCode.ATTRIBUTE_OR_VALUE_EXISTS) {
            return new ObjectAlreadyExistsException(message, e);
        }
        if (rc == ResultCode.INSUFFICIENT_ACCESS_RIGHTS || rc == ResultCode.UNWILLING_TO_PERFORM
                || rc == ResultCode.AUTHORIZATION_DENIED) {
            return new InsufficientAccessException(message, e);
        }
        if (rc == ResultCode.TIMEOUT || rc == ResultCode.TIME_LIMIT_EXCEEDED
                || rc == ResultCode.CONNECT_ERROR || rc == ResultCode.SERVER_DOWN) {
            return new OperationTimeoutException(message, e);
        }
        if (rc == ResultCode.SIZE_LIMIT_EXCEEDED || rc == ResultCode.ADMIN_LIMIT_EXCEEDED) {
            return new SizeLimitExceededException(message, e);
        }
        return new IamIntegrationException(message, e);
    }
}
