package com.tongji.enso.mybatisdemo.controller;

import com.tongji.enso.mybatisdemo.entity.admin.AdminApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {AdminAuthController.class, AdminForecastResultImageController.class})
public class AdminExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AdminApiResponse<Object>> handleResponseStatusException(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "请求处理失败" : ex.getReason();
        return new ResponseEntity<>(AdminApiResponse.fail(message), ex.getStatus());
    }
}
