package com.github.guoyyit.deviceownershiptransferjava.security;

import com.github.guoyyit.deviceownershiptransferjava.store.StateStore;
import com.github.guoyyit.deviceownershiptransferjava.web.ApiException;
import com.github.guoyyit.deviceownershiptransferjava.web.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
public class ReplayGuard {

    private static final long MAX_SKEW_MILLIS = 5L * 60 * 1000;

    private final StateStore store;

    public ReplayGuard(StateStore store) {
        this.store = store;
    }

    public void checkAndRecord(String nonce, long timestamp) {
        if (nonce == null || nonce.isBlank() || nonce.length() > 128) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "nonce is required");
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - timestamp) > MAX_SKEW_MILLIS) {
            throw new ApiException(ErrorCode.STALE_TIMESTAMP, "request timestamp outside allowed window");
        }
        synchronized (store) {
            pruneExpired(now);
            if (store.state().usedNonces.containsKey(nonce)) {
                throw new ApiException(ErrorCode.REPLAY_DETECTED, "nonce already used");
            }
            store.state().usedNonces.put(nonce, now);
            store.save();
        }
    }

    private void pruneExpired(long now) {
        Iterator<Map.Entry<String, Long>> iterator = store.state().usedNonces.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() > MAX_SKEW_MILLIS * 2) {
                iterator.remove();
            }
        }
    }
}
