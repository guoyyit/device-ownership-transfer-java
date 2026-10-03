package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.config.AppProperties;
import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.Payloads;
import com.github.guoyyit.deviceownershiptransferjava.store.DeviceEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.IssuerEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.NonceEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.OwnerEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.SessionEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final StateStore stateStore;
    private final CryptoService cryptoService;
    private final AppProperties properties;
    private final AuditService auditService;

    public SessionService(StateStore stateStore, CryptoService cryptoService, AppProperties properties,
                          AuditService auditService) {
        this.stateStore = stateStore;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.auditService = auditService;
    }

    public CreatedSession createSession(String deviceId, String nonce, long timestamp, String ownerSignature) {
        long now = System.currentTimeMillis();
        DeviceEntity device = stateStore.findDevice(deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCode.DEVICE_NOT_REGISTERED,
                        "device not registered: " + deviceId));
        validateTimestamp(timestamp, now);
        validateNonce(nonce, "SESSION_CREATE", deviceId);

        OwnerEntity currentOwner = stateStore.findOwner(device.ownerId)
                .orElseThrow(() -> new ServiceException(ErrorCode.OWNER_NOT_FOUND,
                        "current owner not found: " + device.ownerId));
        String payload = Payloads.sessionCreate(deviceId, currentOwner.publicKey, nonce, timestamp);
        if (!cryptoService.verify(currentOwner.publicKey, payload, ownerSignature)) {
            throw new ServiceException(ErrorCode.INVALID_SIGNATURE,
                    "invalid session signature or signer is not the current owner");
        }
        markNonceUsed(nonce, "SESSION_CREATE", deviceId, now);

        SessionEntity session = new SessionEntity();
        session.sessionId = "ses-" + UUID.randomUUID();
        session.deviceId = deviceId;
        session.ownerId = currentOwner.ownerId;
        session.epoch = device.epoch;
        session.issuedAt = now;
        session.expiresAt = now + properties.getSessionTtlMs();
        session.revoked = false;
        stateStore.saveSession(session);

        String tokenPayload = Payloads.sessionToken(session.sessionId, deviceId, currentOwner.ownerId,
                session.epoch, session.issuedAt, session.expiresAt);
        IssuerEntity issuer = stateStore.getIssuer();
        String tokenSignature = cryptoService.sign(issuer.privateKey, tokenPayload);
        String token = cryptoService.encodeToken(tokenPayload, tokenSignature);

        auditService.record("SESSION_CREATED", deviceId, currentOwner.ownerId,
                "session " + session.sessionId + " created");
        log.info("session {} created for device {} by owner {}", session.sessionId, deviceId, currentOwner.ownerId);
        return new CreatedSession(session, token);
    }

    public VerifyResult verifySession(String sessionId) {
        long now = System.currentTimeMillis();
        SessionEntity session = stateStore.findSession(sessionId)
                .orElseThrow(() -> new ServiceException(ErrorCode.SESSION_NOT_FOUND,
                        "session not found: " + sessionId));
        if (session.revoked) {
            return new VerifyResult(false, session, "REVOKED");
        }
        if (session.expiresAt <= now) {
            return new VerifyResult(false, session, "EXPIRED");
        }
        DeviceEntity device = stateStore.findDevice(session.deviceId)
                .orElseThrow(() -> new ServiceException(ErrorCode.DEVICE_NOT_REGISTERED,
                        "device not registered: " + session.deviceId));
        if (session.epoch != device.epoch) {
            return new VerifyResult(false, session, "EPOCH_MISMATCH");
        }
        return new VerifyResult(true, session, null);
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

    public record CreatedSession(SessionEntity session, String token) {
    }

    public record VerifyResult(boolean valid, SessionEntity session, String reason) {
    }
}
