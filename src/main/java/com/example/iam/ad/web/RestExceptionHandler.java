package com.example.iam.ad.web;

import com.example.iam.ad.exception.IamIntegrationException;
import com.example.iam.ad.exception.InsufficientAccessException;
import com.example.iam.ad.exception.ObjectAlreadyExistsException;
import com.example.iam.ad.exception.ObjectNotFoundException;
import com.example.iam.ad.exception.OperationTimeoutException;
import com.example.iam.ad.exception.SizeLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps the connector's typed exception hierarchy onto HTTP problem details. */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    @ExceptionHandler(ObjectNotFoundException.class)
    public ProblemDetail notFound(ObjectNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ObjectAlreadyExistsException.class)
    public ProblemDetail conflict(ObjectAlreadyExistsException e) {
        return problem(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(InsufficientAccessException.class)
    public ProblemDetail forbidden(InsufficientAccessException e) {
        return problem(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(OperationTimeoutException.class)
    public ProblemDetail gatewayTimeout(OperationTimeoutException e) {
        return problem(HttpStatus.GATEWAY_TIMEOUT, e);
    }

    @ExceptionHandler(SizeLimitExceededException.class)
    public ProblemDetail badRequest(SizeLimitExceededException e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalidInput(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    /** Catch-all for directory failures not covered above. */
    @ExceptionHandler(IamIntegrationException.class)
    public ProblemDetail badGateway(IamIntegrationException e) {
        log.error("Unmapped directory failure surfaced to REST layer", e);
        return problem(HttpStatus.BAD_GATEWAY, e);
    }

    private ProblemDetail problem(HttpStatus status, Exception e) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        detail.setTitle(e.getClass().getSimpleName());
        return detail;
    }
}
