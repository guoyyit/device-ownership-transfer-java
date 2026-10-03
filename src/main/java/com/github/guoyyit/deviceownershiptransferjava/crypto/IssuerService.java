package com.github.guoyyit.deviceownershiptransferjava.crypto;

import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.UUID;

@Component
public class IssuerService {

    private static final long CREDENTIAL_TTL_MILLIS = 24L * 60 * 60 * 1000;

    private final CryptoService crypto;
    private final StateStore store;
    private PrivateKey issuerPrivateKey;
    private PublicKey issuerPublicKey;

    public IssuerService(CryptoService crypto, StateStore store) {
        this.crypto = crypto;
        this.store = store;
    }

    @PostConstruct
    public void init() {
        synchronized (this) {
            if (store.state().issuerPrivateKey == null) {
                KeyPair keyPair = crypto.generateKeyPair();
                store.state().issuerPrivateKey = crypto.encodePrivateKey(keyPair.getPrivate());
                store.state().issuerPublicKey = crypto.encodePublicKey(keyPair.getPublic());
                store.save();
            }
            issuerPrivateKey = crypto.decodePrivateKey(store.state().issuerPrivateKey);
            issuerPublicKey = crypto.decodePublicKey(store.state().issuerPublicKey);
        }
    }

    public OwnershipCredential issue(String deviceId, String devicePublicKey, String ownerId) {
        long now = System.currentTimeMillis();
        OwnershipCredential unsigned = new OwnershipCredential(
                UUID.randomUUID().toString(),
                deviceId,
                devicePublicKey,
                ownerId,
                now,
                now + CREDENTIAL_TTL_MILLIS,
                null);
        byte[] signature = crypto.sign(issuerPrivateKey, unsigned.canonicalPayload().getBytes(StandardCharsets.UTF_8));
        return new OwnershipCredential(
                unsigned.credentialId(),
                unsigned.deviceId(),
                unsigned.devicePublicKey(),
                unsigned.ownerId(),
                unsigned.issuedAt(),
                unsigned.expiresAt(),
                Base64.getEncoder().encodeToString(signature));
    }

    public boolean verify(OwnershipCredential credential) {
        if (credential == null || credential.signature() == null) {
            return false;
        }
        final byte[] signature;
        try {
            signature = Base64.getDecoder().decode(credential.signature());
        } catch (IllegalArgumentException e) {
            return false;
        }
        return crypto.verify(issuerPublicKey,
                credential.canonicalPayload().getBytes(StandardCharsets.UTF_8), signature);
    }

    public String issuerPublicKey() {
        return crypto.encodePublicKey(issuerPublicKey);
    }
}
