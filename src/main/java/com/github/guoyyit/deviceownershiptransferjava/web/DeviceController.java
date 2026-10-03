package com.github.guoyyit.deviceownershiptransferjava.web;

import com.github.guoyyit.deviceownershiptransferjava.domain.Device;
import com.github.guoyyit.deviceownershiptransferjava.domain.Transfer;
import com.github.guoyyit.deviceownershiptransferjava.security.ReplayGuard;
import com.github.guoyyit.deviceownershiptransferjava.service.AuditService;
import com.github.guoyyit.deviceownershiptransferjava.service.DeviceService;
import com.github.guoyyit.deviceownershiptransferjava.service.TransferService;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.Requests.DeviceActionRequest;
import com.github.guoyyit.deviceownershiptransferjava.web.dto.Requests.RegisterDeviceRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final TransferService transferService;
    private final AuditService auditService;
    private final ReplayGuard replayGuard;

    public DeviceController(DeviceService deviceService, TransferService transferService,
                            AuditService auditService, ReplayGuard replayGuard) {
        this.deviceService = deviceService;
        this.transferService = transferService;
        this.auditService = auditService;
        this.replayGuard = replayGuard;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterDeviceRequest request) {
        replayGuard.checkAndRecord(request.nonce(), request.timestamp());
        Device device = deviceService.register(request.deviceId(), request.publicKey(), request.credential());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deviceId", device.deviceId);
        response.put("ownerId", device.ownerId);
        response.put("registeredAt", device.registeredAt);
        return response;
    }

    @GetMapping("/{deviceId}/owner")
    public Map<String, Object> currentOwner(@PathVariable String deviceId) {
        Device device = deviceService.getDevice(deviceId);
        return Map.of("deviceId", device.deviceId, "ownerId", device.ownerId);
    }

    @GetMapping("/{deviceId}/transfers")
    public Object transferHistory(@PathVariable String deviceId) {
        return transferService.historyForDevice(deviceId);
    }

    @GetMapping("/{deviceId}/audit")
    public Object auditLog(@PathVariable String deviceId) {
        deviceService.getDevice(deviceId);
        return auditService.forDevice(deviceId);
    }

    @PostMapping("/{deviceId}/pending-transfer")
    public Map<String, Object> pendingTransfer(@PathVariable String deviceId,
                                               @RequestBody DeviceActionRequest request) {
        transferService.verifyDevicePoll(deviceId, request.signature(), request.nonce(), request.timestamp());
        Transfer pending = transferService.pendingForDevice(deviceId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deviceId", deviceId);
        response.put("pending", pending);
        return response;
    }
}
