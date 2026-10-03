package com.github.guoyyit.deviceownershiptransferjava.web;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    CREDENTIAL_INVALID(HttpStatus.BAD_REQUEST),
    CREDENTIAL_EXPIRED(HttpStatus.BAD_REQUEST),
    CREDENTIAL_REUSED(HttpStatus.CONFLICT),
    CREDENTIAL_DEVICE_MISMATCH(HttpStatus.BAD_REQUEST),
    DEVICE_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND),
    OWNER_NOT_FOUND(HttpStatus.NOT_FOUND),
    SESSION_INVALID(HttpStatus.UNAUTHORIZED),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED),
    CREDENTIALS_INVALID(HttpStatus.UNAUTHORIZED),
    SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED),
    REPLAY_DETECTED(HttpStatus.CONFLICT),
    STALE_TIMESTAMP(HttpStatus.BAD_REQUEST),
    NOT_DEVICE_OWNER(HttpStatus.FORBIDDEN),
    TRANSFER_CONFLICT(HttpStatus.CONFLICT),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND),
    TRANSFER_NOT_PENDING(HttpStatus.CONFLICT),
    TRANSFER_FORBIDDEN(HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
