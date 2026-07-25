package com.tongji.enso.mybatisdemo.admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** 让管理后台拿到可读的错误原因，而不是 Spring Boot 默认隐藏的异常信息。 */
@RestControllerAdvice(basePackages = "com.tongji.enso.mybatisdemo.admin")
public class AdminExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = ex.getStatus();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getReason() == null ? status.getReasonPhrase() : ex.getReason());
        return ResponseEntity.status(status).body(body);
    }
}
