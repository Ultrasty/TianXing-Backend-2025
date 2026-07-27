package com.tongji.enso.mybatisdemo.admin.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminApiResponse<T> {
    private final Object code;
    private final String message;
    private final T data;
    private final Map<String, Object> details;

    private AdminApiResponse(Object code, String message, T data, Map<String, Object> details) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.details = details;
    }

    public static <T> AdminApiResponse<T> success(T data) {
        return new AdminApiResponse<>(0, "success", data, null);
    }

    public static AdminApiResponse<Void> failure(String code, String message) {
        return failure(code, message, Collections.emptyMap());
    }

    public static AdminApiResponse<Void> failure(String code, String message, Map<String, Object> details) {
        return new AdminApiResponse<>(code, message, null, details == null || details.isEmpty() ? null : details);
    }

    public Object getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
