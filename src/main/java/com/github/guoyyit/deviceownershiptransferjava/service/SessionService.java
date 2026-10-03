package com.github.guoyyit.deviceownershiptransferjava.service;

import com.github.guoyyit.deviceownershiptransferjava.crypto.CryptoService;
import com.github.guoyyit.deviceownershiptransferjava.domain.OwnerAccount;
import com.github.guoyyit.deviceownershiptransferjava.domain.Session;
import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SessionService {

    private static final long SESSION_TTL_MILLIS = 60L * 60 * 1000;

    private final StateStore store;
    private final CryptoService crypto;

    public SessionService(StateStore store, CryptoService crypto) {
        this.store = store;
        this.crypto = crypto;
    }

    public OwnerAccount createOwner() {
        synchronized (store) {
            OwnerAccount owner = new OwnerAccount();
            owner.ownerId = "owner-" + UUID.randomUUID();
            owner.secret = crypto.randomToken();
            owner.createdAt = System.currentTimeMillis();
            store.state().owners.put(owner.ownerId, owner);
            store.save();
            return owner;
        }
    }

    public Session createSession(String ownerId, String secret) {
        synchronized (store) {
            OwnerAccount owner = store.state().owners.get(ownerId);
            if (owner == null) {
                throw new ApiException(ErrorCode.OWNER_NOT_FOUND, "owner not found");
            }
            if (!owner.secret.equals(secret)) {
                throw new ApiException(ErrorCode.CREDENTIALS_INVALID, "invalid owner secret");
            }
            Session session = new Session();
            session.token = crypto.randomToken();
            session.ownerId = ownerId;
            session.issuedAt = System.currentTimeMillis();
            session.expiresAt = session.issuedAt + SESSION_TTL_MILLIS;
            session.revoked = false;
            store.state().sessions.put(session.token, session);
            store.save();
            return session;
        }
    }

    public String authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw new ApiException(ErrorCode.SESSION_INVALID, "missing session token");
        }
        synchronized (store) {
            Session session = store.state().sessions.get(token);
            if (session == null) {
                throw new ApiException(ErrorCode.SESSION_INVALID, "unknown session token");
            }
            if (session.revoked) {
                throw new ApiException(ErrorCode.SESSION_REVOKED, "session has been revoked");
            }
            if (session.expiresAt < System.currentTimeMillis()) {
                throw new ApiException(ErrorCode.SESSION_INVALID, "session expired");
            }
            return session.ownerId;
        }
    }

    public void revokeAllForOwner(String ownerId) {
        synchronized (store) {
            boolean changed = false;
            for (Session session : store.state().sessions.values()) {
                if (session.ownerId.equals(ownerId) && !session.revoked) {
                    session.revoked = true;
                    changed = true;
                }
            }
            if (changed) {
                store.save();
            }
        }
    }
}
