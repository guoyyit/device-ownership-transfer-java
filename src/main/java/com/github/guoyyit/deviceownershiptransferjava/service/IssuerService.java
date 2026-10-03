package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.config.AppProperties;
import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.Keys;
import com.github.guoyyit.deviceownershiptransferjava.crypto.Payloads;
import com.github.guoyyit.deviceownershiptransferjava.store.IssuerEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.OwnerEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IssuerService {

    private final StateStore stateStore;
    private final CryptoService cryptoService;
    private final AppProperties properties;

    public IssuerService(StateStore stateStore, CryptoService cryptoService, AppProperties properties) {
        this.stateStore = stateStore;
        this.cryptoService = cryptoService;
        this.properties = properties;
    }

    public IssuerEntity getIssuer() {
        return stateStore.getIssuer();
    }

    public ProvisionedDevice issueDevice(String ownerId) {
        OwnerEntity owner = stateStore.findOwner(ownerId)
                .orElseThrow(() -> new ServiceException(ErrorCode.OWNER_NOT_FOUND, "owner not found: " + ownerId));
        Keys deviceKeys = cryptoService.generateKeyPair();
        String deviceId = "dev-" + UUID.randomUUID();
        String credentialNonce = "non-" + UUID.randomUUID();
        long expiresAt = System.currentTimeMillis() + properties.getCredentialTtlMs();

        String credentialPayload = Payloads.ownershipCredential(deviceId, deviceKeys.publicKey(),
                owner.ownerId, owner.publicKey, credentialNonce, expiresAt);
        IssuerEntity issuer = stateStore.getIssuer();
        String signature = cryptoService.sign(issuer.privateKey, credentialPayload);
        String credential = cryptoService.encodeToken(credentialPayload, signature);

        return new ProvisionedDevice(deviceId, deviceKeys.publicKey(), deviceKeys.privateKey(),
                credential, credentialNonce, expiresAt);
    }

    public record ProvisionedDevice(String deviceId, String devicePublicKey, String devicePrivateKey,
                                     String credential, String credentialNonce, long credentialExpiresAt) {
    }
}
