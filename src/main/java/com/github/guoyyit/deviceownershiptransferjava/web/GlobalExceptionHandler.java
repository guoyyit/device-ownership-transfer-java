package com.github.guoyyit.deviceownershiptransferjava.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.code().status())
                .body(Map.of("code", ex.code().name(), "message", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex) {
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(Map.of("code", ErrorCode.INTERNAL_ERROR.name(), "message", "internal error"));
    }
}
