package com.github.guoyyit.deviceownershiptransferjava.crypto;

public record OwnershipCredential(
        String credentialId,
        String deviceId,
        String devicePublicKey,
        String ownerId,
        long issuedAt,
        long expiresAt,
        String signature) {

    public String canonicalPayload() {
        return String.join("|",
                credentialId, deviceId, devicePublicKey, ownerId,
                String.valueOf(issuedAt), String.valueOf(expiresAt));
    }
}
