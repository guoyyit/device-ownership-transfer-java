package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record PendingTransferRequest(String nonce,
                                     long timestamp,
                                     String deviceSignature) {
}
