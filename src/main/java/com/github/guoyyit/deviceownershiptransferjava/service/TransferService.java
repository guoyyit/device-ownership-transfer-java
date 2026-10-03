package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.config.AppProperties;
import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.Payloads;
import com.github.guoyyit.deviceownershiptransferjava.store.DeviceEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.NonceEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.OwnerEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.SessionEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import com.github.guoyyit.deviceownershiptransferjava.store.TransferEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final StateStore stateStore;
    private final CryptoService cryptoService;
    private final AppProperties properties;
    private final AuditService auditService;
    private final ConcurrentHashMap<String, ReentrantLock> deviceLocks = new ConcurrentHashMap<>();

    public TransferService(StateStore stateStore, CryptoService cryptoService, AppProperties properties,
                           AuditService auditService) {
        this.stateStore = stateStore;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.auditService = auditService;
    }

    private ReentrantLock lockFor(String deviceId) {
        return deviceLocks.computeIfAbsent(deviceId, k -> new ReentrantLock());
    }

    public TransferEntity initiate(String deviceId, String newOwnerPublicKey, String nonce,
                                   long timestamp, String ownerSignature) {
        ReentrantLock lock = lockFor(deviceId);
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            DeviceEntity device = stateStore.findDevice(deviceId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.DEVICE_NOT_REGISTERED,
                            "device not registered: " + deviceId));
            validateTimestamp(timestamp, now);
            validateNonce(nonce, "TRANSFER_INITIATE", deviceId);

            OwnerEntity newOwner = stateStore.findOwnerByPublicKey(newOwnerPublicKey)
                    .orElseThrow(() -> new ServiceException(ErrorCode.INVALID_TRANSFER_TARGET,
                            "no owner bound to the given public key"));

            String payload = Payloads.transferInitiate(deviceId, newOwnerPublicKey, nonce, timestamp);
            OwnerEntity currentOwner = stateStore.findOwner(device.ownerId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.OWNER_NOT_FOUND,
                            "current owner not found: " + device.ownerId));
            if (!cryptoService.verify(currentOwner.publicKey, payload, ownerSignature)) {
                throw new ServiceException(ErrorCode.INVALID_SIGNATURE,
                        "invalid transfer initiate signature or signer is not the current owner");
            }

            if (stateStore.findPendingTransfer(deviceId).isPresent()) {
                throw new ServiceException(ErrorCode.PENDING_TRANSFER_EXISTS,
                        "a pending transfer already exists for device: " + deviceId);
            }

            markNonceUsed(nonce, "TRANSFER_INITIATE", deviceId, now);

            TransferEntity transfer = new TransferEntity();
            transfer.transferId = "tr-" + UUID.randomUUID();
            transfer.deviceId = deviceId;
            transfer.fromOwnerId = device.ownerId;
            transfer.toOwnerId = newOwner.ownerId;
            transfer.status = "PENDING";
            transfer.initiateNonce = nonce;
            transfer.createdAt = now;
            transfer.updatedAt = now;
            stateStore.saveTransfer(transfer);

            auditService.record("TRANSFER_INITIATED", deviceId, currentOwner.ownerId,
                    "transfer " + transfer.transferId + " initiated to owner " + newOwner.ownerId);
            log.info("transfer {} initiated for device {}: {} -> {}", transfer.transferId, deviceId,
                    transfer.fromOwnerId, transfer.toOwnerId);
            return transfer;
        } finally {
            lock.unlock();
        }
    }

    public TransferEntity fetchPending(String deviceId, String nonce, long timestamp, String deviceSignature) {
        ReentrantLock lock = lockFor(deviceId);
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            DeviceEntity device = stateStore.findDevice(deviceId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.DEVICE_NOT_REGISTERED,
                            "device not registered: " + deviceId));
            validateTimestamp(timestamp, now);
            validateNonce(nonce, "TRANSFER_PENDING_FETCH", deviceId);

            String payload = Payloads.transferPending(deviceId, nonce, timestamp);
            if (!cryptoService.verify(device.publicKey, payload, deviceSignature)) {
                throw new ServiceException(ErrorCode.INVALID_SIGNATURE, "invalid pending-fetch signature");
            }
            markNonceUsed(nonce, "TRANSFER_PENDING_FETCH", deviceId, now);

            return stateStore.findPendingTransfer(deviceId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.TRANSFER_NOT_FOUND,
                            "no pending transfer for device: " + deviceId));
        } finally {
            lock.unlock();
        }
    }

    public TransferEntity confirm(String deviceId, String transferId, String nonce,
                                  long timestamp, String deviceSignature) {
        ReentrantLock lock = lockFor(deviceId);
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            DeviceEntity device = stateStore.findDevice(deviceId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.DEVICE_NOT_REGISTERED,
                            "device not registered: " + deviceId));
            validateTimestamp(timestamp, now);
            validateNonce(nonce, "TRANSFER_CONFIRM", deviceId);

            String payload = Payloads.transferConfirm(transferId, deviceId, nonce, timestamp);
            if (!cryptoService.verify(device.publicKey, payload, deviceSignature)) {
                throw new ServiceException(ErrorCode.INVALID_SIGNATURE, "invalid transfer confirm signature");
            }
            markNonceUsed(nonce, "TRANSFER_CONFIRM", deviceId, now);

            TransferEntity transfer = stateStore.findTransfer(transferId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.TRANSFER_NOT_FOUND,
                            "transfer not found: " + transferId));
            if (!transfer.deviceId.equals(deviceId)) {
                throw new ServiceException(ErrorCode.TRANSFER_NOT_FOUND,
                        "transfer does not belong to device: " + deviceId);
            }
            if (!"PENDING".equals(transfer.status)) {
                throw new ServiceException(ErrorCode.TRANSFER_NOT_PENDING,
                        "transfer is not pending, current status: " + transfer.status);
            }

            transfer.status = "CONFIRMED";
            transfer.updatedAt = now;
            transfer.confirmNonce = nonce;
            stateStore.saveTransfer(transfer);

            String previousOwnerId = device.ownerId;
            device.ownerId = transfer.toOwnerId;
            device.epoch = device.epoch + 1;
            stateStore.saveDevice(device);

            revokeOwnerSessions(deviceId, previousOwnerId, now);

            auditService.record("TRANSFER_CONFIRMED", deviceId, transfer.toOwnerId,
                    "transfer " + transfer.transferId + " confirmed; owner changed "
                            + previousOwnerId + " -> " + transfer.toOwnerId + "; epoch=" + device.epoch);
            log.info("transfer {} confirmed for device {}, owner {} -> {}", transfer.transferId, deviceId,
                    previousOwnerId, transfer.toOwnerId);
            return transfer;
        } finally {
            lock.unlock();
        }
    }

    public TransferEntity reject(String deviceId, String transferId, String nonce,
                                 long timestamp, String deviceSignature) {
        ReentrantLock lock = lockFor(deviceId);
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            DeviceEntity device = stateStore.findDevice(deviceId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.DEVICE_NOT_REGISTERED,
                            "device not registered: " + deviceId));
            validateTimestamp(timestamp, now);
            validateNonce(nonce, "TRANSFER_REJECT", deviceId);

            String payload = Payloads.transferReject(transferId, deviceId, nonce, timestamp);
            if (!cryptoService.verify(device.publicKey, payload, deviceSignature)) {
                throw new ServiceException(ErrorCode.INVALID_SIGNATURE, "invalid transfer reject signature");
            }
            markNonceUsed(nonce, "TRANSFER_REJECT", deviceId, now);

            TransferEntity transfer = stateStore.findTransfer(transferId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.TRANSFER_NOT_FOUND,
                            "transfer not found: " + transferId));
            if (!transfer.deviceId.equals(deviceId)) {
                throw new ServiceException(ErrorCode.TRANSFER_NOT_FOUND,
                        "transfer does not belong to device: " + deviceId);
            }
            if (!"PENDING".equals(transfer.status)) {
                throw new ServiceException(ErrorCode.TRANSFER_NOT_PENDING,
                        "transfer is not pending, current status: " + transfer.status);
            }

            transfer.status = "REJECTED";
            transfer.updatedAt = now;
            transfer.rejectNonce = nonce;
            stateStore.saveTransfer(transfer);

            auditService.record("TRANSFER_REJECTED", deviceId, device.ownerId,
                    "transfer " + transfer.transferId + " rejected by device");
            log.info("transfer {} rejected for device {}", transfer.transferId, deviceId);
            return transfer;
        } finally {
            lock.unlock();
        }
    }

    public TransferEntity cancel(String deviceId, String transferId, String nonce,
                                 long timestamp, String ownerSignature) {
        ReentrantLock lock = lockFor(deviceId);
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            DeviceEntity device = stateStore.findDevice(deviceId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.DEVICE_NOT_REGISTERED,
                            "device not registered: " + deviceId));
            validateTimestamp(timestamp, now);
            validateNonce(nonce, "TRANSFER_CANCEL", deviceId);

            TransferEntity transfer = stateStore.findTransfer(transferId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.TRANSFER_NOT_FOUND,
                            "transfer not found: " + transferId));
            if (!transfer.deviceId.equals(deviceId)) {
                throw new ServiceException(ErrorCode.TRANSFER_NOT_FOUND,
                        "transfer does not belong to device: " + deviceId);
            }
            if (!"PENDING".equals(transfer.status)) {
                throw new ServiceException(ErrorCode.TRANSFER_NOT_PENDING,
                        "transfer is not pending, current status: " + transfer.status);
            }

            String payload = Payloads.transferCancel(transferId, deviceId, nonce, timestamp);
            OwnerEntity currentOwner = stateStore.findOwner(device.ownerId)
                    .orElseThrow(() -> new ServiceException(ErrorCode.OWNER_NOT_FOUND,
                            "current owner not found: " + device.ownerId));
            if (!cryptoService.verify(currentOwner.publicKey, payload, ownerSignature)) {
                throw new ServiceException(ErrorCode.INVALID_SIGNATURE,
                        "invalid transfer cancel signature or signer is not the current owner");
            }
            if (!transfer.fromOwnerId.equals(device.ownerId)) {
                throw new ServiceException(ErrorCode.NOT_OWNER,
                        "only the initiating owner can cancel this transfer");
            }
            markNonceUsed(nonce, "TRANSFER_CANCEL", deviceId, now);

            transfer.status = "CANCELLED";
            transfer.updatedAt = now;
            transfer.cancelNonce = nonce;
            stateStore.saveTransfer(transfer);

            auditService.record("TRANSFER_CANCELLED", deviceId, currentOwner.ownerId,
                    "transfer " + transfer.transferId + " cancelled by owner");
            log.info("transfer {} cancelled for device {}", transfer.transferId, deviceId);
            return transfer;
        } finally {
            lock.unlock();
        }
    }

    public List<TransferEntity> history(String deviceId) {
        stateStore.findDevice(deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCode.DEVICE_NOT_FOUND, "device not found: " + deviceId));
        return stateStore.listTransfers(deviceId);
    }

    private void revokeOwnerSessions(String deviceId, String ownerId, long now) {
        List<SessionEntity> sessions = stateStore.listSessionsForOwner(deviceId, ownerId);
        for (SessionEntity session : sessions) {
            if (!session.revoked) {
                session.revoked = true;
                stateStore.saveSession(session);
            }
        }
    }

    private void validateTimestamp(long timestamp, long now) {
        long window = properties.getTimestampWindowMs();
        if (Math.abs(now - timestamp) > window) {
            throw new ServiceException(ErrorCode.STALE_TIMESTAMP,
                    "timestamp outside allowed window: " + timestamp);
        }
    }

    private void validateNonce(String nonce, String context, String subject) {
        if (stateStore.isNonceUsed(nonce)) {
            throw new ServiceException(ErrorCode.NONCE_REUSED,
                    "nonce already used (" + context + "): " + nonce);
        }
    }

    private void markNonceUsed(String nonce, String context, String subject, long now) {
        NonceEntity entity = new NonceEntity();
        entity.nonce = nonce;
        entity.context = context;
        entity.subject = subject;
        entity.usedAt = now;
        stateStore.markNonceUsed(entity);
    }
}
