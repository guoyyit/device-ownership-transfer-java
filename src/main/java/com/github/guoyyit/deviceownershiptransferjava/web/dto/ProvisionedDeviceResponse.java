package com.github.guoyyit.deviceownershiptransferjava.web.dto;

public record ProvisionedDeviceResponse(String deviceId,
                                        String devicePublicKey,
                                        String devicePrivateKey,
                                        String credential,
                                        String credentialNonce,
                                        long credentialExpiresAt) {
}
