package com.cyberstock.common;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Domain error carrying a stable error code. The frontend translates the code into the
 * user's language, so no human-readable text is sent from the backend (AGENT.MD §4.5).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> params;

    public ApiException(HttpStatus status, String code) {
        this(status, code, Map.of());
    }

    public ApiException(HttpStatus status, String code, Map<String, Object> params) {
        super(code);
        this.status = status;
        this.code = code;
        this.params = params;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> params() {
        return params;
    }
}
