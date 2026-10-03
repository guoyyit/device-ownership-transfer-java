package com.github.guoyyit.deviceownershiptransferjava.tests;

import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.IssuerService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.OwnershipCredential;
import com.github.guoyyit.deviceownershiptransferjava.domain.Device;
import com.github.guoyyit.deviceownershiptransferjava.domain.OwnerAccount;
import com.github.guoyyit.deviceownershiptransferjava.domain.Transfer;
import com.github.guoyyit.deviceownershiptransferjava.security.ReplayGuard;
import com.github.guoyyit.deviceownershiptransferjava.service.AuditService;
import com.github.guoyyit.deviceownershiptransferjava.service.DeviceService;
import com.github.guoyyit.deviceownershiptransferjava.service.SessionService;
import com.github.guoyyit.deviceownershiptransferjava.service.TransferService;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;
import java.util.Comparator;
import java.util.UUID;

class TestServices {

    final StateStore store;
    final CryptoService crypto;
    final IssuerService issuer;
    final AuditService audit;
    final ReplayGuard replayGuard;
    final SessionService sessions;
    final DeviceService devices;
    final TransferService transfers;

    private TestServices(Path storePath) {
        this.store = new StateStore(storePath.toString());
        store.load();
        this.crypto = new CryptoService();
        this.issuer = new IssuerService(crypto, store);
        issuer.init();
        this.audit = new AuditService(store);
        this.replayGuard = new ReplayGuard(store);
        this.sessions = new SessionService(store, crypto);
        this.devices = new DeviceService(store, issuer, crypto, audit);
        this.transfers = new TransferService(store, sessions, audit, replayGuard, crypto);
    }

    static TestServices fresh(Path dir) {
        deleteRecursively(dir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return new TestServices(dir.resolve("state.json"));
    }

    static TestServices restart(Path dir) {
        return new TestServices(dir.resolve("state.json"));
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    OwnerAccount newOwner() {
        return sessions.createOwner();
    }

    String sessionToken(OwnerAccount owner) {
        return sessions.createSession(owner.ownerId, owner.secret).token;
    }

    KeyPair newDeviceKeys() {
        return crypto.generateKeyPair();
    }

    String publicKeyOf(KeyPair keys) {
        return crypto.encodePublicKey(keys.getPublic());
    }

    OwnershipCredential issueCredential(String deviceId, KeyPair keys, OwnerAccount owner) {
        return issuer.issue(deviceId, publicKeyOf(keys), owner.ownerId);
    }

    Device registerDevice(String deviceId, KeyPair keys, OwnerAccount owner) {
        OwnershipCredential credential = issueCredential(deviceId, keys, owner);
        return devices.register(deviceId, publicKeyOf(keys), credential);
    }

    String uniqueNonce() {
        return UUID.randomUUID().toString();
    }

    long now() {
        return System.currentTimeMillis();
    }

    String sign(KeyPair keys, String payload) {
        return Base64.getEncoder().encodeToString(
                crypto.sign(keys.getPrivate(), payload.getBytes(StandardCharsets.UTF_8)));
    }

    String confirmPayload(Transfer transfer, String nonce, long timestamp) {
        return "CONFIRM|" + transfer.transferId + "|" + transfer.confirmChallenge + "|" + nonce + "|" + timestamp;
    }

    String rejectPayload(Transfer transfer, String nonce, long timestamp) {
        return "REJECT|" + transfer.transferId + "|" + transfer.confirmChallenge + "|" + nonce + "|" + timestamp;
    }
}
