package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.IssuerService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.OwnershipCredential;
import com.github.guoyyit.deviceownershiptransferjava.domain.Device;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    private final StateStore store;
    private final IssuerService issuer;
    private final CryptoService crypto;
    private final AuditService audit;

    public DeviceService(StateStore store, IssuerService issuer, CryptoService crypto, AuditService audit) {
        this.store = store;
        this.issuer = issuer;
        this.crypto = crypto;
        this.audit = audit;
    }

    public Device register(String deviceId, String publicKey, OwnershipCredential credential) {
        if (deviceId == null || deviceId.isBlank() || publicKey == null || publicKey.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "deviceId and publicKey are required");
        }
        crypto.decodePublicKey(publicKey);
        if (credential == null) {
            throw new ApiException(ErrorCode.CREDENTIAL_INVALID, "ownership credential is required");
        }
        synchronized (store) {
            if (store.state().devices.containsKey(deviceId)) {
                throw new ApiException(ErrorCode.DEVICE_ALREADY_REGISTERED, "device already registered");
            }
            if (!issuer.verify(credential)) {
                throw new ApiException(ErrorCode.CREDENTIAL_INVALID, "credential signature verification failed");
            }
            if (!deviceId.equals(credential.deviceId()) || !publicKey.equals(credential.devicePublicKey())) {
                throw new ApiException(ErrorCode.CREDENTIAL_DEVICE_MISMATCH,
                        "credential does not match device identity");
            }
            if (credential.expiresAt() < System.currentTimeMillis()) {
                throw new ApiException(ErrorCode.CREDENTIAL_EXPIRED, "credential expired");
            }
            if (store.state().consumedCredentials.contains(credential.credentialId())) {
                throw new ApiException(ErrorCode.CREDENTIAL_REUSED, "credential already consumed");
            }
            Device device = new Device();
            device.deviceId = deviceId;
            device.publicKey = publicKey;
            device.ownerId = credential.ownerId();
            device.registeredAt = System.currentTimeMillis();
            device.credentialId = credential.credentialId();
            store.state().devices.put(deviceId, device);
            store.state().consumedCredentials.add(credential.credentialId());
            store.save();
            audit.record(deviceId, null, "DEVICE_REGISTERED", credential.ownerId(),
                    "device registered and bound to first owner");
            return device;
        }
    }

    public Device getDevice(String deviceId) {
        synchronized (store) {
            Device device = store.state().devices.get(deviceId);
            if (device == null) {
                throw new ApiException(ErrorCode.DEVICE_NOT_FOUND, "device not found");
            }
            return device;
        }
    }

    public String currentOwner(String deviceId) {
        return getDevice(deviceId).ownerId;
    }
}
