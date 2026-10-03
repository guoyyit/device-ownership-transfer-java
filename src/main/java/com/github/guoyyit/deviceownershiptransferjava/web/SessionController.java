package com.github.guoyyit.deviceownershiptransferjava.web;

import com.github.guoyyit.deviceownershiptransferjava.service.SessionService;
import com.github.guoyyit.deviceownershiptransferjava.store.SessionEntity;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.CreateSessionRequest;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.SessionResponse;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.SessionVerifyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/api/devices/{deviceId}/sessions")
    public ResponseEntity<SessionResponse> createSession(@PathVariable String deviceId,
                                                        @RequestBody CreateSessionRequest request) {
        SessionService.CreatedSession created = sessionService.createSession(deviceId,
                request.nonce(), request.timestamp(), request.ownerSignature());
        SessionEntity session = created.session();
        return ResponseEntity.ok(new SessionResponse(session.sessionId, session.deviceId, session.ownerId,
                session.epoch, session.issuedAt, session.expiresAt, created.token()));
    }

    @PostMapping("/api/sessions/{sessionId}/verify")
    public ResponseEntity<SessionVerifyResponse> verifySession(@PathVariable String sessionId) {
        SessionService.VerifyResult result = sessionService.verifySession(sessionId);
        SessionEntity session = result.session();
        if (result.valid()) {
            return ResponseEntity.ok(new SessionVerifyResponse(true, session.sessionId, session.deviceId,
                    session.ownerId, session.epoch, null));
        }
        return ResponseEntity.ok(new SessionVerifyResponse(false, session.sessionId, session.deviceId,
                session.ownerId, session.epoch, result.reason()));
    }
}
