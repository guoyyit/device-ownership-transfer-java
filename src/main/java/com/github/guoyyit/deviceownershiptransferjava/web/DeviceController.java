package com.github.guoyyit.deviceownershiptransferjava.web;

import com.github.guoyyit.deviceownershiptransferjava.service.AuditService;
import com.github.guoyyit.deviceownershiptransferjava.service.DeviceService;
import com.github.guoyyit.deviceownershiptransferjava.service.OwnerService;
import com.github.guoyyit.deviceownershiptransferjava.store.AuditEntry;
import com.github.guoyyit.deviceownershiptransferjava.store.DeviceEntity;
import com.github.guoyyit.deviceownershiptransferjava.store.OwnerEntity;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.DeviceResponse;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.RegisterDeviceRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final OwnerService ownerService;
    private final AuditService auditService;

    public DeviceController(DeviceService deviceService, OwnerService ownerService, AuditService auditService) {
        this.deviceService = deviceService;
        this.ownerService = ownerService;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    public ResponseEntity<DeviceResponse> register(@RequestBody RegisterDeviceRequest request) {
        DeviceEntity device = deviceService.registerDevice(request.deviceId(), request.devicePublicKey(),
                request.credential(), request.nonce(), request.timestamp(), request.deviceSignature());
        return ResponseEntity.ok(toResponse(device));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable String deviceId) {
        DeviceEntity device = deviceService.getDevice(deviceId);
        return ResponseEntity.ok(toResponse(device));
    }

    @GetMapping("/{deviceId}/audit")
    public ResponseEntity<List<AuditEntry>> getAudit(@PathVariable String deviceId) {
        deviceService.getDevice(deviceId);
        return ResponseEntity.ok(auditService.listForDevice(deviceId));
    }

    private DeviceResponse toResponse(DeviceEntity device) {
        OwnerEntity owner = ownerService.getOwner(device.ownerId);
        return new DeviceResponse(device.deviceId, device.publicKey, device.ownerId, owner.name,
                device.epoch, device.registeredAt);
    }
}
