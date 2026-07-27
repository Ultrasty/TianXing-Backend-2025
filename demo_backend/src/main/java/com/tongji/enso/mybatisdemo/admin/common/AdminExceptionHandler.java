package com.tongji.enso.mybatisdemo.admin.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Collections;

@RestControllerAdvice(basePackages = "com.tongji.enso.mybatisdemo.admin")
public class AdminExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminExceptionHandler.class);

    @ExceptionHandler(AdminException.class)
    public ResponseEntity<AdminApiResponse<Void>> handleAdminException(AdminException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(AdminApiResponse.failure(exception.getCode(), exception.getMessage(), exception.getDetails()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            HttpMediaTypeNotSupportedException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<AdminApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(AdminApiResponse.failure(
                "EVALUATION_INVALID_DATA", "请求参数或JSON格式不正确"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<AdminApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(AdminApiResponse.failure(
                "IMPORT_FILE_TOO_LARGE", "上传文件超过大小限制"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<AdminApiResponse<Void>> handleDuplicateKey(DuplicateKeyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(AdminApiResponse.failure(
                "EVALUATION_DUPLICATE", "评估数据自然键已存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AdminApiResponse<Void>> handleUnexpected(Exception exception) {
        LOGGER.error("Unhandled admin API error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(AdminApiResponse.failure(
                "DATABASE_OPERATION_FAILED", "服务暂时无法完成该操作", Collections.emptyMap()));
    }
}
