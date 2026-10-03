package com.github.guoyyit.deviceownershiptransferjava.crypto;

public final class Payloads {

    private static final String VERSION = "v1";

    private Payloads() {
    }

    public static String register(String deviceId, String devicePublicKey, String nonce, long timestamp) {
        return String.join("|", "DOT-REGISTER-" + VERSION, deviceId, devicePublicKey, nonce, Long.toString(timestamp));
    }

    public static String ownershipCredential(String deviceId, String devicePublicKey, String ownerId,
                                             String ownerPublicKey, String nonce, long expiresAt) {
        return String.join("|", "DOT-OWNERSHIP-CREDENTIAL-" + VERSION, deviceId, devicePublicKey,
                ownerId, ownerPublicKey, nonce, Long.toString(expiresAt));
    }

    public static String transferInitiate(String deviceId, String newOwnerPublicKey, String nonce, long timestamp) {
        return String.join("|", "DOT-TRANSFER-INITIATE-" + VERSION, deviceId, newOwnerPublicKey,
                nonce, Long.toString(timestamp));
    }

    public static String transferPending(String deviceId, String nonce, long timestamp) {
        return String.join("|", "DOT-TRANSFER-PENDING-" + VERSION, deviceId, nonce, Long.toString(timestamp));
    }

    public static String transferConfirm(String transferId, String deviceId, String nonce, long timestamp) {
        return String.join("|", "DOT-TRANSFER-CONFIRM-" + VERSION, transferId, deviceId,
                nonce, Long.toString(timestamp));
    }

    public static String transferReject(String transferId, String deviceId, String nonce, long timestamp) {
        return String.join("|", "DOT-TRANSFER-REJECT-" + VERSION, transferId, deviceId,
                nonce, Long.toString(timestamp));
    }

    public static String transferCancel(String transferId, String deviceId, String nonce, long timestamp) {
        return String.join("|", "DOT-TRANSFER-CANCEL-" + VERSION, transferId, deviceId,
                nonce, Long.toString(timestamp));
    }

    public static String sessionCreate(String deviceId, String ownerPublicKey, String nonce, long timestamp) {
        return String.join("|", "DOT-SESSION-CREATE-" + VERSION, deviceId, ownerPublicKey,
                nonce, Long.toString(timestamp));
    }

    public static String sessionToken(String sessionId, String deviceId, String ownerId, long epoch,
                                      long issuedAt, long expiresAt) {
        return String.join("|", "DOT-SESSION-TOKEN-" + VERSION, sessionId, deviceId, ownerId,
                Long.toString(epoch), Long.toString(issuedAt), Long.toString(expiresAt));
    }
}
