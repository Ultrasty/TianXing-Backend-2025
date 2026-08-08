package com.tongji.enso.mybatisdemo.admin.common;

import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

public class AdminException extends RuntimeException {
    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public AdminException(String code, String message, HttpStatus status) {
        this(code, message, status, Collections.emptyMap());
    }

    public AdminException(String code, String message, HttpStatus status, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details == null ? Collections.emptyMap() : details;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
