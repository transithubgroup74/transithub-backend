package com.transithub.backend.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns ApiException into a JSON body the app can read. Without this an auth
 * failure came back as a bare 403 with no body, which is why the app couldn't
 * tell "wrong password" apart from "server unreachable".
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", ex.getMessage());
        body.put("code", ex.getCode());
        return ResponseEntity.status(ex.getStatus()).body(body);
    }
}
