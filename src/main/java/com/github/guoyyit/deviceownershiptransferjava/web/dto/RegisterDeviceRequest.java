package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record RegisterDeviceRequest(String deviceId,
                                    String devicePublicKey,
                                    String credential,
                                    String nonce,
                                    long timestamp,
                                    String deviceSignature) {
}
