package com.usm.workorder.exception;

/** Thrown for ownership checks @PreAuthorize can't express - e.g. "assigned technician only". */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
