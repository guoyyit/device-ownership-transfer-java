package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record CreateSessionRequest(String nonce,
                                   long timestamp,
                                   String ownerSignature) {
}
