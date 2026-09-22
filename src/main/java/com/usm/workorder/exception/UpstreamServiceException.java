package com.usm.workorder.exception;

/**
 * Thrown when the call to service-request-service (via ServiceRequestClient)
 * fails or times out - kept distinct from InvalidRequestException so it's
 * obvious in logs/tests that the failure is a cross-service integration
 * problem, not bad input from the caller.
 */
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public UpstreamServiceException(String message) {
        super(message);
    }
}
