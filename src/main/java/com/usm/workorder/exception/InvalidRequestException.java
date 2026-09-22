package com.usm.workorder.exception;

/** Thrown for business-rule violations that must be a 400 (e.g. BR-06, BR-08). */
public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
