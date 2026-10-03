package com.github.guoyyit.deviceownershiptransferjava.web;

import com.github.guoyyit.deviceownershiptransferjava.service.ErrorCode;
import com.github.guoyyit.deviceownershiptransferjava.service.ServiceException;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorResponse> handleServiceException(ServiceException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = HttpStatus.valueOf(errorCode.getHttpStatus());
        ErrorResponse body = new ErrorResponse(errorCode.getCode(), errorCode.name(), ex.getMessage());
        log.warn("service error: {} - {}", errorCode.name(), ex.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ErrorResponse body = new ErrorResponse(ErrorCode.INVALID_REQUEST.getCode(),
                ErrorCode.INVALID_REQUEST.name(), ex.getMessage());
        log.warn("invalid request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
