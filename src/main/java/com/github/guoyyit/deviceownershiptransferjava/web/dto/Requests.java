package com.github.guoyyit.deviceownershiptransferjava.web.dto;

import com.github.guoyyit.deviceownershiptransferjava.crypto.OwnershipCredential;

public final class Requests {

    private Requests() {
    }

    public record RegisterDeviceRequest(
            String deviceId,
            String publicKey,
            OwnershipCredential credential,
            String nonce,
            long timestamp) {
    }

    public record IssueCredentialRequest(
            String deviceId,
            String devicePublicKey,
            String ownerId) {
    }

    public record CreateSessionRequest(
            String ownerId,
            String secret) {
    }

    public record InitiateTransferRequest(
            String toOwnerId,
            String nonce,
            long timestamp) {
    }

    public record DeviceActionRequest(
            String deviceId,
            String signature,
            String nonce,
            long timestamp) {
    }

    public record CancelTransferRequest(
            String nonce,
            long timestamp) {
    }
}
