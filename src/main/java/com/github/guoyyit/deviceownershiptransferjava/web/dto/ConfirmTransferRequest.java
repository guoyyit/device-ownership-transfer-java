package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record ConfirmTransferRequest(String transferId,
                                     String nonce,
                                     long timestamp,
                                     String deviceSignature) {
}
