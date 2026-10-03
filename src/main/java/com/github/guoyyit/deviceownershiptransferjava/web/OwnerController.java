package com.github.guoyyit.deviceownershiptransferjava.web;

import com.github.guoyyit.deviceownershiptransferjava.crypto.IssuerService;
import com.github.guoyyit.deviceownershiptransferjava.crypto.OwnershipCredential;
import com.github.guoyyit.deviceownershiptransferjava.domain.OwnerAccount;
import com.github.guoyyit.deviceownershiptransferjava.domain.Session;
import com.github.guoyyit.deviceownershiptransferjava.service.SessionService;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.Requests.CreateSessionRequest;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.Requests.IssueCredentialRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OwnerController {

    private final SessionService sessionService;
    private final IssuerService issuerService;

    public OwnerController(SessionService sessionService, IssuerService issuerService) {
        this.sessionService = sessionService;
        this.issuerService = issuerService;
    }

    @PostMapping("/owners")
    public Object createOwner() {
        OwnerAccount owner = sessionService.createOwner();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ownerId", owner.ownerId);
        response.put("secret", owner.secret);
        return response;
    }

    @PostMapping("/sessions")
    public Object createSession(@RequestBody CreateSessionRequest request) {
        Session session = sessionService.createSession(request.ownerId(), request.secret());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", session.token);
        response.put("ownerId", session.ownerId);
        response.put("expiresAt", session.expiresAt);
        return response;
    }

    @PostMapping("/credentials")
    public Object issueCredential(@RequestBody IssueCredentialRequest request) {
        OwnershipCredential credential = issuerService.issue(
                request.deviceId(), request.devicePublicKey(), request.ownerId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("credential", credential);
        response.put("issuerPublicKey", issuerService.issuerPublicKey());
        return response;
    }
}
