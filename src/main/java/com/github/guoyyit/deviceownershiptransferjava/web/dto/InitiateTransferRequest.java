package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record InitiateTransferRequest(String newOwnerPublicKey,
                                      String nonce,
                                      long timestamp,
                                      String ownerSignature) {
}
