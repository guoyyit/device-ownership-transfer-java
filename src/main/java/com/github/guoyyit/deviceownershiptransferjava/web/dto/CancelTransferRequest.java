package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record CancelTransferRequest(String transferId,
                                    String nonce,
                                    long timestamp,
                                    String ownerSignature) {
}
