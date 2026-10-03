package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.config.AppProperties;
import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.Payloads;
import com.github.guoyyit.deviceownershiptransferjava.store.DeviceEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.IssuerEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.NonceEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final StateStore stateStore;
    private final CryptoService cryptoService;
    private final AppProperties properties;
    private final AuditService auditService;

    public DeviceService(StateStore stateStore, CryptoService cryptoService, AppProperties properties,
                         AuditService auditService) {
        this.stateStore = stateStore;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.auditService = auditService;
    }

    public DeviceEntity registerDevice(String deviceId, String devicePublicKey, String credential,
                                       String nonce, long timestamp, String deviceSignature) {
        long now = System.currentTimeMillis();

        if (stateStore.findDevice(deviceId).isPresent()) {
            throw new ServiceException(ErrorCode.DEVICE_ALREADY_REGISTERED, "device already registered: " + deviceId);
        }
        validateTimestamp(timestamp, now);
        validateNonce(nonce, "REGISTER", deviceId);

        String registerPayload = Payloads.register(deviceId, devicePublicKey, nonce, timestamp);
        if (!cryptoService.verify(devicePublicKey, registerPayload, deviceSignature)) {
            throw new ServiceException(ErrorCode.INVALID_SIGNATURE, "invalid device registration signature");
        }

        IssuerEntity issuer = stateStore.getIssuer();
        String[] decoded = cryptoService.decodeToken(credential);
        String credentialPayload = decoded[0];
        String credentialSignature = decoded[1];
        if (!cryptoService.verify(issuer.publicKey, credentialPayload, credentialSignature)) {
            throw new ServiceException(ErrorCode.CREDENTIAL_INVALID, "invalid ownership credential signature");
        }

        String[] parts = credentialPayload.split("\\|");
        if (parts.length != 7 || !"DOT-OWNERSHIP-CREDENTIAL-v1".equals(parts[0])) {
            throw new ServiceException(ErrorCode.CREDENTIAL_INVALID, "malformed ownership credential");
        }
        String credDeviceId = parts[1];
        String credDevicePublicKey = parts[2];
        String credOwnerId = parts[3];
        String credOwnerPublicKey = parts[4];
        String credNonce = parts[5];
        long credExpiresAt = Long.parseLong(parts[6]);

        if (!credDeviceId.equals(deviceId) || !credDevicePublicKey.equals(devicePublicKey)) {
            throw new ServiceException(ErrorCode.CREDENTIAL_DEVICE_MISMATCH,
                    "credential device identity does not match submitted device");
        }
        if (credExpiresAt <= now) {
            throw new ServiceException(ErrorCode.CREDENTIAL_EXPIRED, "ownership credential expired");
        }
        validateNonce(credNonce, "CREDENTIAL", deviceId);

        stateStore.findOwner(credOwnerId)
                .orElseThrow(() -> new ServiceException(ErrorCode.OWNER_NOT_FOUND,
                        "credential owner not found: " + credOwnerId));

        markNonceUsed(nonce, "REGISTER", deviceId, now);
        markNonceUsed(credNonce, "CREDENTIAL", deviceId, now);

        DeviceEntity device = new DeviceEntity();
        device.deviceId = deviceId;
        device.publicKey = devicePublicKey;
        device.ownerId = credOwnerId;
        device.epoch = 0;
        device.registeredAt = now;
        device.credentialNonce = credNonce;
        stateStore.saveDevice(device);

        auditService.record("DEVICE_REGISTERED", deviceId, credOwnerId,
                "device registered and bound to owner " + credOwnerId);
        log.info("device {} registered, first owner {}", deviceId, credOwnerId);
        return device;
    }

    public DeviceEntity getDevice(String deviceId) {
        return stateStore.findDevice(deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCode.DEVICE_NOT_FOUND, "device not found: " + deviceId));
    }

    void validateTimestamp(long timestamp, long now) {
        long window = properties.getTimestampWindowMs();
        if (Math.abs(now - timestamp) > window) {
            throw new ServiceException(ErrorCode.STALE_TIMESTAMP,
                    "timestamp outside allowed window: " + timestamp);
        }
    }

    void validateNonce(String nonce, String context, String subject) {
        if (stateStore.isNonceUsed(nonce)) {
            throw new ServiceException(ErrorCode.NONCE_REUSED,
                    "nonce already used (" + context + "): " + nonce);
        }
    }

    void markNonceUsed(String nonce, String context, String subject, long now) {
        NonceEntity entity = new NonceEntity();
        entity.nonce = nonce;
        entity.context = context;
        entity.subject = subject;
        entity.usedAt = now;
        stateStore.markNonceUsed(entity);
    }
}
