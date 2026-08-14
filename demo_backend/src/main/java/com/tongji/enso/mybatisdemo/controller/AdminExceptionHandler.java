package com.tongji.enso.mybatisdemo.controller;

import com.tongji.enso.mybatisdemo.admin.common.AdminApiResponse;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class AdminExceptionHandler {

    @ExceptionHandler(AdminException.class)
    public ResponseEntity<AdminApiResponse<Void>> handleAdminException(AdminException ex) {
        AdminApiResponse<Void> response = AdminApiResponse.failure(ex.getCode(), ex.getMessage(), ex.getDetails());
        return new ResponseEntity<>(response, ex.getStatus());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<com.tongji.enso.mybatisdemo.entity.admin.AdminApiResponse<Object>> handleResponseStatusException(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "请求处理失败" : ex.getReason();
        return new ResponseEntity<>(com.tongji.enso.mybatisdemo.entity.admin.AdminApiResponse.fail(message), ex.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AdminApiResponse<Void>> handleGeneralException(Exception ex) {
        AdminApiResponse<Void> response = AdminApiResponse.failure("INTERNAL_ERROR", ex.getMessage());
        return new ResponseEntity<>(response, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
