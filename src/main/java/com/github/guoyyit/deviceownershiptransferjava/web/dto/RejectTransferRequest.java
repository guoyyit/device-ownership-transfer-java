package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record RejectTransferRequest(String transferId,
                                    String nonce,
                                    long timestamp,
                                    String deviceSignature) {
}
