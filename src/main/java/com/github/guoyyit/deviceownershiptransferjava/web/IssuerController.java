package com.github.guoyyit.deviceownershiptransferjava.web;

import com.github.guoyyit.deviceownershiptransferjava.service.IssuerService;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.ProvisionDeviceRequest;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.ProvisionedDeviceResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/issuer")
public class IssuerController {

    private final IssuerService issuerService;

    public IssuerController(IssuerService issuerService) {
        this.issuerService = issuerService;
    }

    @PostMapping("/devices")
    public ResponseEntity<ProvisionedDeviceResponse> provisionDevice(@RequestBody ProvisionDeviceRequest request) {
        IssuerService.ProvisionedDevice provisioned = issuerService.issueDevice(request.ownerId());
        return ResponseEntity.ok(new ProvisionedDeviceResponse(provisioned.deviceId(),
                provisioned.devicePublicKey(), provisioned.devicePrivateKey(), provisioned.credential(),
                provisioned.credentialNonce(), provisioned.credentialExpiresAt()));
    }
}
