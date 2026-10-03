package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.domain.Device;
import com.github.guoyyit.deviceownershiptransferjava.domain.Transfer;
import com.github.guoyyit.deviceownershiptransferjava.domain.TransferStatus;
import com.github.guoyyit.deviceownershiptransferjava.security.ReplayGuard;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class TransferService {

    private final StateStore store;
    private final SessionService sessions;
    private final AuditService audit;
    private final ReplayGuard replayGuard;
    private final CryptoService crypto;

    public TransferService(StateStore store, SessionService sessions, AuditService audit,
                           ReplayGuard replayGuard, CryptoService crypto) {
        this.store = store;
        this.sessions = sessions;
        this.audit = audit;
        this.replayGuard = replayGuard;
        this.crypto = crypto;
    }

    public Transfer initiate(String deviceId, String toOwnerId, String ownerToken, String nonce, long timestamp) {
        replayGuard.checkAndRecord(nonce, timestamp);
        String ownerId = sessions.authenticate(ownerToken);
        if (toOwnerId == null || toOwnerId.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "toOwnerId is required");
        }
        synchronized (store) {
            Device device = store.state().devices.get(deviceId);
            if (device == null) {
                throw new ApiException(ErrorCode.DEVICE_NOT_FOUND, "device not found");
            }
            if (!device.ownerId.equals(ownerId)) {
                throw new ApiException(ErrorCode.NOT_DEVICE_OWNER, "only the current owner can initiate a transfer");
            }
            if (device.ownerId.equals(toOwnerId)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "cannot transfer to the current owner");
            }
            if (!store.state().owners.containsKey(toOwnerId)) {
                throw new ApiException(ErrorCode.OWNER_NOT_FOUND, "target owner not found");
            }
            if (findPending(deviceId) != null) {
                throw new ApiException(ErrorCode.TRANSFER_CONFLICT, "a pending transfer already exists for this device");
            }
            Transfer transfer = new Transfer();
            transfer.transferId = UUID.randomUUID().toString();
            transfer.deviceId = deviceId;
            transfer.fromOwnerId = ownerId;
            transfer.toOwnerId = toOwnerId;
            transfer.status = TransferStatus.PENDING;
            transfer.createdAt = System.currentTimeMillis();
            transfer.updatedAt = transfer.createdAt;
            transfer.confirmChallenge = crypto.randomToken();
            store.state().transfers.put(transfer.transferId, transfer);
            store.save();
            audit.record(deviceId, transfer.transferId, "TRANSFER_INITIATED", ownerId,
                    "transfer to " + toOwnerId + " initiated");
            return transfer;
        }
    }

    public Transfer confirm(String transferId, String deviceId, String signature, String nonce, long timestamp) {
        replayGuard.checkAndRecord(nonce, timestamp);
        synchronized (store) {
            Transfer transfer = getPendingTransferForDeviceAction(transferId, deviceId);
            verifyDeviceSignature(transfer, "CONFIRM", signature, nonce, timestamp);
            Device device = store.state().devices.get(transfer.deviceId);
            transfer.status = TransferStatus.COMPLETED;
            transfer.updatedAt = System.currentTimeMillis();
            device.ownerId = transfer.toOwnerId;
            store.save();
            sessions.revokeAllForOwner(transfer.fromOwnerId);
            audit.record(transfer.deviceId, transferId, "TRANSFER_COMPLETED", transfer.toOwnerId,
                    "ownership switched from " + transfer.fromOwnerId + " to " + transfer.toOwnerId
                            + "; previous owner sessions revoked");
            return transfer;
        }
    }

    public Transfer reject(String transferId, String deviceId, String signature, String nonce, long timestamp) {
        replayGuard.checkAndRecord(nonce, timestamp);
        synchronized (store) {
            Transfer transfer = getPendingTransferForDeviceAction(transferId, deviceId);
            verifyDeviceSignature(transfer, "REJECT", signature, nonce, timestamp);
            transfer.status = TransferStatus.REJECTED;
            transfer.updatedAt = System.currentTimeMillis();
            store.save();
            audit.record(transfer.deviceId, transferId, "TRANSFER_REJECTED", transfer.deviceId,
                    "device rejected the transfer");
            return transfer;
        }
    }

    public Transfer cancel(String transferId, String ownerToken, String nonce, long timestamp) {
        replayGuard.checkAndRecord(nonce, timestamp);
        String ownerId = sessions.authenticate(ownerToken);
        synchronized (store) {
            Transfer transfer = store.state().transfers.get(transferId);
            if (transfer == null) {
                throw new ApiException(ErrorCode.TRANSFER_NOT_FOUND, "transfer not found");
            }
            if (!transfer.fromOwnerId.equals(ownerId)) {
                throw new ApiException(ErrorCode.TRANSFER_FORBIDDEN, "only the initiator can cancel the transfer");
            }
            if (transfer.status != TransferStatus.PENDING) {
                throw new ApiException(ErrorCode.TRANSFER_NOT_PENDING, "transfer is no longer pending");
            }
            transfer.status = TransferStatus.CANCELLED;
            transfer.updatedAt = System.currentTimeMillis();
            store.save();
            audit.record(transfer.deviceId, transferId, "TRANSFER_CANCELLED", ownerId,
                    "initiator cancelled the transfer");
            return transfer;
        }
    }

    public Transfer pendingForDevice(String deviceId) {
        synchronized (store) {
            if (!store.state().devices.containsKey(deviceId)) {
                throw new ApiException(ErrorCode.DEVICE_NOT_FOUND, "device not found");
            }
            return findPending(deviceId);
        }
    }

    public void verifyDevicePoll(String deviceId, String signature, String nonce, long timestamp) {
        replayGuard.checkAndRecord(nonce, timestamp);
        synchronized (store) {
            Device device = store.state().devices.get(deviceId);
            if (device == null) {
                throw new ApiException(ErrorCode.DEVICE_NOT_FOUND, "device not found");
            }
            verifySignature(device.publicKey, "POLL|" + deviceId + "|" + nonce + "|" + timestamp, signature);
        }
    }

    public List<Transfer> historyForDevice(String deviceId) {
        synchronized (store) {
            if (!store.state().devices.containsKey(deviceId)) {
                throw new ApiException(ErrorCode.DEVICE_NOT_FOUND, "device not found");
            }
            List<Transfer> result = new ArrayList<>();
            for (Transfer transfer : store.state().transfers.values()) {
                if (transfer.deviceId.equals(deviceId)) {
                    result.add(transfer);
                }
            }
            result.sort(Comparator.comparingLong(t -> t.createdAt));
            return result;
        }
    }

    private Transfer findPending(String deviceId) {
        for (Transfer transfer : store.state().transfers.values()) {
            if (transfer.deviceId.equals(deviceId) && transfer.status == TransferStatus.PENDING) {
                return transfer;
            }
        }
        return null;
    }

    private Transfer getPendingTransferForDeviceAction(String transferId, String deviceId) {
        Transfer transfer = store.state().transfers.get(transferId);
        if (transfer == null) {
            throw new ApiException(ErrorCode.TRANSFER_NOT_FOUND, "transfer not found");
        }
        if (!transfer.deviceId.equals(deviceId)) {
            throw new ApiException(ErrorCode.TRANSFER_FORBIDDEN, "transfer does not belong to this device");
        }
        if (transfer.status != TransferStatus.PENDING) {
            throw new ApiException(ErrorCode.TRANSFER_NOT_PENDING, "transfer is no longer pending");
        }
        return transfer;
    }

    private void verifyDeviceSignature(Transfer transfer, String action, String signature,
                                       String nonce, long timestamp) {
        Device device = store.state().devices.get(transfer.deviceId);
        String payload = action + "|" + transfer.transferId + "|" + transfer.confirmChallenge
                + "|" + nonce + "|" + timestamp;
        verifySignature(device.publicKey, payload, signature);
    }

    private void verifySignature(String devicePublicKey, String payload, String signature) {
        final byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signature == null ? "" : signature);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.SIGNATURE_INVALID, "malformed signature");
        }
        PublicKey publicKey = crypto.decodePublicKey(devicePublicKey);
        if (!crypto.verify(publicKey, payload.getBytes(StandardCharsets.UTF_8), signatureBytes)) {
            throw new ApiException(ErrorCode.SIGNATURE_INVALID, "device signature verification failed");
        }
    }
}
