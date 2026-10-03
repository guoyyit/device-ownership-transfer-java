package com.github.guoyyit.deviceownershiptransferjava.service;

public enum ErrorCode {
    INVALID_REQUEST(40000, 400),
    INVALID_SIGNATURE(40001, 401),
    CREDENTIAL_INVALID(40002, 401),
    CREDENTIAL_EXPIRED(40003, 401),
    CREDENTIAL_DEVICE_MISMATCH(40004, 400),
    DEVICE_ALREADY_REGISTERED(40005, 409),
    DEVICE_NOT_REGISTERED(40006, 404),
    OWNER_NOT_FOUND(40007, 404),
    DEVICE_NOT_FOUND(40008, 404),
    NONCE_REUSED(40009, 409),
    STALE_TIMESTAMP(40010, 401),
    NOT_OWNER(40011, 403),
    PENDING_TRANSFER_EXISTS(40012, 409),
    TRANSFER_NOT_FOUND(40013, 404),
    TRANSFER_NOT_PENDING(40014, 409),
    TRANSFER_ALREADY_FINALIZED(40015, 409),
    SESSION_NOT_FOUND(40016, 404),
    SESSION_REVOKED(40017, 401),
    SESSION_EXPIRED(40018, 401),
    SESSION_EPOCH_MISMATCH(40019, 401),
    INVALID_TRANSFER_TARGET(40020, 400);

    private final int code;
    private final int httpStatus;

    ErrorCode(int code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
