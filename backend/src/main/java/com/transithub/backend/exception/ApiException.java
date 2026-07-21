package com.transithub.backend.exception;

/**
 * An error that is safe to show the user, carrying the HTTP status and a short
 * machine-readable code so the app can react (e.g. send them to the verify
 * screen) instead of just printing a message.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
